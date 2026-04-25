"""
Step 2 of the nightly pipeline: transcription via faster-whisper large-v3.

Usage:
    python transcribe.py 2026-04-10

Reads:  AUDIO_ARCHIVE_DIR/<date>/diarization.json
        AUDIO_PENDING_DIR/<date>/<files>.aac  (converted to WAV per worker)
Writes: AUDIO_ARCHIVE_DIR/<date>/transcription.json
"""

import json
import subprocess
import sys
import tempfile
from concurrent.futures import ProcessPoolExecutor, as_completed
from pathlib import Path
from typing import Optional

from config import (
    AUDIO_ARCHIVE_DIR,
    AUDIO_PENDING_DIR,
    MAX_WORKERS,
    NO_SPEECH_PROB_THRESHOLD,
    WHISPER_BEAM_SIZE,
    WHISPER_CONDITION_ON_PREVIOUS_TEXT,
    WHISPER_INITIAL_PROMPT,
    WHISPER_LANGUAGE,
    WHISPER_MODEL,
    WHISPER_MODEL_DIR,
    WHISPER_TASK,
    WHISPER_VAD_FILTER,
)


_worker_model = None


def _get_model():
    """Return the cached model for this worker process, loading it on first call."""
    global _worker_model
    if _worker_model is None:
        from faster_whisper import WhisperModel  # type: ignore
        _worker_model = WhisperModel(
            WHISPER_MODEL,
            device='cpu',
            compute_type='int8',
            download_root=WHISPER_MODEL_DIR,
        )
    return _worker_model


def transcribe_segment(
    audio_path: str,
    start: float,
    end: float,
) -> dict:
    """
    Transcribe a single time-bounded segment of *audio_path*.

    Extracts the segment to a temp WAV, runs Whisper, returns:
        {'text': str, 'language': str, 'confidence': float, 'language_probability': float}
    """
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tmp:
        tmp_path = tmp.name

    try:
        subprocess.run(
            [
                'ffmpeg', '-y',
                '-i', audio_path,
                '-ss', str(start),
                '-t', str(end - start),
                '-ar', '16000', '-ac', '1',
                tmp_path,
            ],
            check=True,
            capture_output=True,
        )

        model = _get_model()
        segments_iter, info = model.transcribe(
            tmp_path,
            task=WHISPER_TASK,
            beam_size=WHISPER_BEAM_SIZE,
            vad_filter=WHISPER_VAD_FILTER,
            language=WHISPER_LANGUAGE or None,
            initial_prompt=WHISPER_INITIAL_PROMPT or None,
            condition_on_previous_text=WHISPER_CONDITION_ON_PREVIOUS_TEXT,
        )
        text_parts = []
        avg_logprob_sum = 0.0
        count = 0

        for seg in segments_iter:
            if seg.no_speech_prob > NO_SPEECH_PROB_THRESHOLD:
                continue
            text_parts.append(seg.text.strip())
            avg_logprob_sum += seg.avg_logprob
            count += 1

        text = ' '.join(text_parts).strip()
        confidence = float(avg_logprob_sum / count) if count else 0.0
        language = info.language or 'unknown'
        language_probability = float(
            getattr(info, 'language_probability', 0.0) or 0.0
        )

    finally:
        Path(tmp_path).unlink(missing_ok=True)

    return {
        'text': text,
        'language': language,
        'confidence': confidence,
        'language_probability': language_probability,
    }


def _transcribe_file_worker(args: tuple) -> tuple[str, list[dict]]:
    """
    Worker function run in a subprocess.
    args = (aac_path_str, segments_list)

    Returns (filename, transcribed_segments).
    """
    aac_path_str, diar_segments = args
    aac_path = Path(aac_path_str)

    with tempfile.TemporaryDirectory() as tmpdir:
        wav_path = str(Path(tmpdir) / f'{aac_path.stem}.wav')
        subprocess.run(
            ['ffmpeg', '-y', '-i', aac_path_str,
             '-ar', '16000', '-ac', '1', wav_path],
            check=True, capture_output=True,
        )

        transcribed: list[dict] = []
        for seg in diar_segments:
            result = transcribe_segment(wav_path, seg['start'], seg['end'])
            if not result['text']:
                continue
            transcribed.append({
                'speaker': seg['speaker'],
                'start': seg['start'],
                'end': seg['end'],
                'text': result['text'],
                'language': result['language'],
                'confidence': result['confidence'],
                'language_probability': result['language_probability'],
            })

    return aac_path.name, transcribed


def run_parallel(
    file_segment_pairs: list[tuple[str, list[dict]]],
) -> dict[str, list[dict]]:
    """
    Transcribe multiple audio files in parallel using ProcessPoolExecutor.
    Each worker loads its own model instance.

    Returns {filename: [transcribed_segment, ...]}
    """
    results: dict[str, list[dict]] = {}

    workers = min(MAX_WORKERS, len(file_segment_pairs))
    with ProcessPoolExecutor(max_workers=workers) as pool:
        futures = {
            pool.submit(_transcribe_file_worker, pair): pair[0]
            for pair in file_segment_pairs
        }
        for future in as_completed(futures):
            aac_path = futures[future]
            try:
                filename, segments = future.result()
                results[filename] = segments
                print(f'[transcribe]   {filename}: {len(segments)} segment(s) transcribed')
            except Exception as exc:
                print(f'[transcribe]   ERROR processing {Path(aac_path).name}: {exc}')

    return results


def run_transcription(date: str) -> None:
    archive_dir = Path(AUDIO_ARCHIVE_DIR) / date
    pending_dir = Path(AUDIO_PENDING_DIR) / date

    diar_path = archive_dir / 'diarization.json'
    if not diar_path.exists():
        print(f'[transcribe] diarization.json not found for {date} — run diarize.py first')
        return

    diarization: dict = json.loads(diar_path.read_text())
    if not diarization:
        print(f'[transcribe] No diarization results for {date}')
        return

    # Build list of (aac_path, diar_segments) pairs
    pairs: list[tuple[str, list[dict]]] = []
    for filename, data in diarization.items():
        aac_path = pending_dir / filename
        if not aac_path.exists():
            print(f'[transcribe]   WARNING: {aac_path} not found — skipping')
            continue
        pairs.append((str(aac_path), data['segments']))

    if not pairs:
        print(f'[transcribe] No valid audio files to transcribe for {date}')
        return

    print(f'[transcribe] Transcribing {len(pairs)} file(s) with up to {MAX_WORKERS} workers')
    results = run_parallel(pairs)

    out_path = archive_dir / 'transcription.json'
    out_path.write_text(json.dumps(results, indent=2))
    print(f'[transcribe] Written → {out_path}')


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print('Usage: python transcribe.py YYYY-MM-DD')
        sys.exit(1)
    run_transcription(sys.argv[1])
