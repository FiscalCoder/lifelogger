"""
Step 3 of the nightly pipeline: speaker identification via SpeechBrain ECAPA-TDNN.

Matches each diarized speaker against the speaker_profiles registry.
Unmatched speakers are inserted into unknown_speakers for the naming UI.

Usage:
    python identify_speakers.py 2026-04-10

Reads:  AUDIO_ARCHIVE_DIR/<date>/diarization.json
        AUDIO_SAMPLE_DIR/<date>_*_<speaker>.wav   (10-sec clips)
Writes: AUDIO_ARCHIVE_DIR/<date>/speaker_ids.json
        DB: unknown_speakers (new rows for unmatched speakers)
        DB: speaker_profiles (embedding update for matched speakers)
"""

import json
import sys
import uuid
from pathlib import Path
from typing import Optional

import numpy as np

from config import (
    AUDIO_ARCHIVE_DIR,
    AUDIO_SAMPLE_DIR,
    ECAPA_MODEL,
    ECAPA_MODEL_DIR,
    ECAPA_SIMILARITY_THRESHOLD,
)
from db import execute, execute_returning, fetch_one, get_connection


# ── Model loading ──────────────────────────────────────────────────────────────

_classifier = None


def _get_classifier():
    global _classifier
    if _classifier is None:
        from speechbrain.inference.speaker import EncoderClassifier  # type: ignore

        _classifier = EncoderClassifier.from_hparams(
            source=ECAPA_MODEL,
            savedir=ECAPA_MODEL_DIR,
        )
    return _classifier


# ── Core functions ─────────────────────────────────────────────────────────────

def extract_embedding(audio_path: str, start: float, end: float) -> np.ndarray:
    """
    Extract a 192-dim ECAPA-TDNN speaker embedding from [start, end] of audio_path.
    Returns a unit-normalised numpy array of shape (192,).
    """
    import subprocess
    import tempfile

    import torchaudio  # type: ignore

    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tmp:
        tmp_path = tmp.name

    try:
        duration = end - start
        subprocess.run(
            [
                'ffmpeg', '-y',
                '-i', audio_path,
                '-ss', str(start),
                '-t', str(max(duration, 1.0)),
                '-ar', '16000', '-ac', '1',
                tmp_path,
            ],
            check=True,
            capture_output=True,
        )

        signal, fs = torchaudio.load(tmp_path)
        classifier = _get_classifier()
        embedding = classifier.encode_batch(signal)
        vec = embedding.squeeze().detach().cpu().numpy()

    finally:
        Path(tmp_path).unlink(missing_ok=True)

    # Unit-normalise for cosine similarity
    norm = np.linalg.norm(vec)
    if norm > 0:
        vec = vec / norm
    return vec.astype(np.float32)


def lookup_speaker(
    embedding: np.ndarray,
    conn,
) -> tuple[Optional[str], Optional[str], float]:
    """
    Query speaker_profiles for the closest match to *embedding*.

    Returns (speaker_id, speaker_name, similarity).
    Returns (None, None, 0.0) if no match exceeds ECAPA_SIMILARITY_THRESHOLD.
    """
    vec_literal = f"[{','.join(str(x) for x in embedding.tolist())}]"
    row = fetch_one(
        """
        SELECT id, name,
               1 - (embedding <=> %s::vector) AS similarity
        FROM speaker_profiles
        ORDER BY embedding <=> %s::vector ASC
        LIMIT 1
        """,
        (vec_literal, vec_literal),
        conn=conn,
    )

    if row is None:
        return None, None, 0.0

    similarity = float(row['similarity'])
    if similarity < ECAPA_SIMILARITY_THRESHOLD:
        return None, None, similarity

    return str(row['id']), str(row['name']), similarity


def update_profile(
    speaker_id: str,
    new_embedding: np.ndarray,
    conn,
) -> None:
    """
    Update a speaker profile's embedding via incremental rolling average.
    Also increments sample_count.
    """
    vec_literal = f"[{','.join(str(x) for x in new_embedding.tolist())}]"
    execute(
        """
        UPDATE speaker_profiles
        SET embedding = (
              (embedding * sample_count + %s::vector) / (sample_count + 1)
            ),
            sample_count = sample_count + 1,
            updated_at = NOW()
        WHERE id = %s::uuid
        """,
        (vec_literal, speaker_id),
        conn=conn,
    )


def create_unknown(
    embedding: np.ndarray,
    audio_sample_filename: Optional[str],
    recorded_at: str,
    conn,
) -> str:
    """
    Insert a new row into unknown_speakers.
    Returns the new row's id.
    """
    vec_literal = f"[{','.join(str(x) for x in embedding.tolist())}]"
    temp_name = f'unknown-{uuid.uuid4().hex[:6]}'
    row = execute_returning(
        """
        INSERT INTO unknown_speakers (temp_name, embedding, audio_sample, recorded_at)
        VALUES (%s, %s::vector, %s, %s::timestamptz)
        RETURNING id, temp_name
        """,
        (temp_name, vec_literal, audio_sample_filename, recorded_at),
        conn=conn,
    )
    return str(row['id'])


# ── Pipeline step ──────────────────────────────────────────────────────────────

def run_speaker_id(date: str) -> None:
    archive_dir = Path(AUDIO_ARCHIVE_DIR) / date
    sample_dir = Path(AUDIO_SAMPLE_DIR)

    diar_path = archive_dir / 'diarization.json'
    if not diar_path.exists():
        print(f'[identify] diarization.json not found for {date} — run diarize.py first')
        return

    diarization: dict = json.loads(diar_path.read_text())

    # speaker_ids maps composite key "filename::SPEAKER_XX" → resolution info
    speaker_ids: dict[str, dict] = {}

    with get_connection() as conn:
        for filename, data in diarization.items():
            sample_paths: dict[str, Optional[str]] = data.get('sample_paths', {})
            speakers_processed: set[str] = set()

            for seg in data.get('segments', []):
                speaker_label = seg['speaker']
                composite_key = f'{filename}::{speaker_label}'

                if composite_key in speakers_processed:
                    continue
                speakers_processed.add(composite_key)

                sample_filename = sample_paths.get(speaker_label)
                if not sample_filename:
                    print(f'[identify]   No sample clip for {speaker_label} in {filename} — skipping')
                    speaker_ids[composite_key] = {
                        'resolved': False,
                        'name': None,
                        'temp_name': None,
                        'similarity': 0.0,
                    }
                    continue

                sample_path = sample_dir / sample_filename
                if not sample_path.exists():
                    print(f'[identify]   Sample clip not found: {sample_path}')
                    speaker_ids[composite_key] = {
                        'resolved': False,
                        'name': None,
                        'temp_name': None,
                        'similarity': 0.0,
                    }
                    continue

                try:
                    embedding = extract_embedding(str(sample_path), 0.0, 10.0)
                except Exception as exc:
                    print(f'[identify]   ERROR extracting embedding for {sample_filename}: {exc}')
                    continue

                speaker_id, speaker_name, similarity = lookup_speaker(embedding, conn)

                if speaker_name:
                    print(f'[identify]   {speaker_label} in {filename} → "{speaker_name}" '
                          f'(similarity={similarity:.3f})')
                    update_profile(speaker_id, embedding, conn)
                    speaker_ids[composite_key] = {
                        'resolved': True,
                        'name': speaker_name,
                        'temp_name': None,
                        'similarity': similarity,
                    }
                else:
                    print(f'[identify]   {speaker_label} in {filename} → UNKNOWN '
                          f'(best similarity={similarity:.3f})')
                    unknown_id = create_unknown(
                        embedding,
                        sample_filename,
                        f'{date}T00:00:00Z',
                        conn,
                    )
                    # Retrieve the auto-generated temp_name
                    row = fetch_one(
                        'SELECT temp_name FROM unknown_speakers WHERE id = %s::uuid',
                        (unknown_id,),
                        conn=conn,
                    )
                    temp_name = row['temp_name'] if row else f'unknown-{uuid.uuid4().hex[:6]}'
                    speaker_ids[composite_key] = {
                        'resolved': False,
                        'name': None,
                        'temp_name': temp_name,
                        'similarity': similarity,
                    }

    out_path = archive_dir / 'speaker_ids.json'
    out_path.write_text(json.dumps(speaker_ids, indent=2))
    print(f'[identify] Written → {out_path}')


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print('Usage: python identify_speakers.py YYYY-MM-DD')
        sys.exit(1)
    run_speaker_id(sys.argv[1])
