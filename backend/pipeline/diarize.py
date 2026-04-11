"""
Step 1 of the nightly pipeline: speaker diarization via pyannote-audio 3.x.

Usage:
    python diarize.py 2026-04-10

Reads:  AUDIO_PENDING_DIR/<date>/*.aac
Writes: AUDIO_ARCHIVE_DIR/<date>/diarization.json
        AUDIO_SAMPLE_DIR/<date>_<stem>_<speaker>.wav  (10-second clips)
"""

import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import Optional

from config import (
    AUDIO_ARCHIVE_DIR,
    AUDIO_PENDING_DIR,
    AUDIO_SAMPLE_DIR,
    HF_TOKEN,
    PYANNOTE_MODEL,
    PYANNOTE_MODEL_DIR,
    SPEAKER_SAMPLE_DURATION,
)


def _convert_to_wav(aac_path: Path, wav_path: Path) -> None:
    """Convert an AAC file to 16kHz mono WAV using ffmpeg."""
    subprocess.run(
        [
            'ffmpeg', '-y',
            '-i', str(aac_path),
            '-ar', '16000',
            '-ac', '1',
            str(wav_path),
        ],
        check=True,
        capture_output=True,
    )


def _load_pipeline():
    """Load the pyannote diarization pipeline (cached after first call)."""
    from pyannote.audio import Pipeline  # type: ignore

    kwargs = {'cache_dir': PYANNOTE_MODEL_DIR}
    if HF_TOKEN:
        kwargs['use_auth_token'] = HF_TOKEN

    pipeline = Pipeline.from_pretrained(PYANNOTE_MODEL, **kwargs)
    return pipeline


def diarize_file(wav_path: Path) -> list[dict]:
    """
    Run diarization on a WAV file.

    Returns a list of segments:
        [{'speaker': 'SPEAKER_00', 'start': 0.0, 'end': 2.3}, ...]
    """
    pipeline = _load_pipeline()
    diarization = pipeline(str(wav_path))
    segments = [
        {'speaker': label, 'start': round(turn.start, 3), 'end': round(turn.end, 3)}
        for turn, _, label in diarization.itertracks(yield_label=True)
    ]
    return segments


def _extract_sample_clip(
    wav_path: Path,
    segments: list[dict],
    speaker: str,
    output_path: Path,
    duration: float = SPEAKER_SAMPLE_DURATION,
) -> Optional[Path]:
    """
    Find the longest contiguous segment for *speaker* and extract up to
    *duration* seconds from it to *output_path*.
    Returns the output path on success, None if no suitable segment found.
    """
    speaker_segs = [s for s in segments if s['speaker'] == speaker]
    if not speaker_segs:
        return None

    # Pick the longest segment
    best = max(speaker_segs, key=lambda s: s['end'] - s['start'])
    start = best['start']
    clip_duration = min(best['end'] - best['start'], duration)

    if clip_duration < 1.0:
        return None

    output_path.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        [
            'ffmpeg', '-y',
            '-i', str(wav_path),
            '-ss', str(start),
            '-t', str(clip_duration),
            '-ar', '16000', '-ac', '1',
            str(output_path),
        ],
        check=True,
        capture_output=True,
    )
    return output_path


def run_diarization(date: str) -> None:
    """
    Diarize all .aac files in AUDIO_PENDING_DIR/<date>/ and write results.

    Exits cleanly if the directory does not exist.
    """
    pending_dir = Path(AUDIO_PENDING_DIR) / date
    archive_dir = Path(AUDIO_ARCHIVE_DIR) / date
    sample_dir = Path(AUDIO_SAMPLE_DIR)

    if not pending_dir.exists():
        print(f'[diarize] No pending dir for {date} — skipping.')
        return

    aac_files = sorted(pending_dir.glob('*.aac'))
    if not aac_files:
        print(f'[diarize] No .aac files found in {pending_dir}')
        return

    archive_dir.mkdir(parents=True, exist_ok=True)
    sample_dir.mkdir(parents=True, exist_ok=True)

    print(f'[diarize] Processing {len(aac_files)} file(s) for {date}')

    results: dict[str, dict] = {}

    with tempfile.TemporaryDirectory() as tmpdir:
        for aac_path in aac_files:
            stem = aac_path.stem
            wav_path = Path(tmpdir) / f'{stem}.wav'

            print(f'[diarize]   Converting {aac_path.name} → WAV')
            try:
                _convert_to_wav(aac_path, wav_path)
            except subprocess.CalledProcessError as exc:
                print(f'[diarize]   ERROR converting {aac_path.name}: {exc.stderr.decode()}')
                continue

            print(f'[diarize]   Diarizing {aac_path.name}')
            try:
                segments = diarize_file(wav_path)
            except Exception as exc:
                print(f'[diarize]   ERROR diarizing {aac_path.name}: {exc}')
                continue

            # Save sample clips for each unique speaker
            speakers_seen = set()
            sample_paths: dict[str, Optional[str]] = {}
            for seg in segments:
                sp = seg['speaker']
                if sp not in speakers_seen:
                    speakers_seen.add(sp)
                    clip_name = f'{date}_{stem}_{sp}.wav'
                    clip_path = sample_dir / clip_name
                    saved = _extract_sample_clip(wav_path, segments, sp, clip_path)
                    sample_paths[sp] = clip_name if saved else None

            results[aac_path.name] = {
                'segments': segments,
                'sample_paths': sample_paths,
            }
            print(f'[diarize]   {aac_path.name}: {len(segments)} segment(s), '
                  f'{len(speakers_seen)} speaker(s)')

    out_path = archive_dir / 'diarization.json'
    out_path.write_text(json.dumps(results, indent=2))
    print(f'[diarize] Written → {out_path}')


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print('Usage: python diarize.py YYYY-MM-DD')
        sys.exit(1)
    run_diarization(sys.argv[1])
