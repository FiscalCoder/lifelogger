"""
Step 1 of the nightly pipeline: speaker diarization via pyannote-audio 3.x.

Usage:
    python diarize.py 2026-04-10

Reads:  AUDIO_PENDING_DIR/<date>/*.aac
Writes: AUDIO_ARCHIVE_DIR/<date>/diarization.json
        AUDIO_SAMPLE_DIR/<date>_<stem>_<speaker>.wav  (10-second clips)
"""

import json
import logging
import os
import subprocess
import sys
import tempfile
import threading
import time
import warnings
import wave
from contextlib import contextmanager
from pathlib import Path
from typing import Iterator, Optional

from config import (
    AUDIO_ARCHIVE_DIR,
    AUDIO_PENDING_DIR,
    AUDIO_SAMPLE_DIR,
    DIARIZATION_HEARTBEAT_SECONDS,
    DIARIZATION_MODE,
    HF_TOKEN,
    PYANNOTE_MODEL,
    PYANNOTE_MODEL_DIR,
    SPEAKER_SAMPLE_DURATION,
)

_pipeline = None


def _log(message: str) -> None:
    """Print a flushed diarization log line."""
    print(f'[diarize] {message}', flush=True)


@contextmanager
def _heartbeat(label: str) -> Iterator[None]:
    """Emit progress logs while a long blocking PyAnnote operation runs."""
    interval = DIARIZATION_HEARTBEAT_SECONDS
    if interval <= 0:
        yield
        return

    stop = threading.Event()
    started = time.monotonic()

    def run() -> None:
        while not stop.wait(interval):
            elapsed = time.monotonic() - started
            _log(f'  Still running {label} elapsed={elapsed:.1f}s')

    thread = threading.Thread(target=run, name='diarization-heartbeat', daemon=True)
    thread.start()
    try:
        yield
    finally:
        stop.set()
        thread.join(timeout=1)


def _configure_pyannote_runtime() -> None:
    """Reduce known noisy PyAnnote/Torch CPU warnings without hiding failures."""
    warning_patterns = (
        r'torchaudio\._backend\..* has been deprecated.*',
        r'`torchaudio\.backend\.common\.AudioMetaData` has been moved.*',
        r'std\(\): degrees of freedom is <= 0\..*',
    )
    for pattern in warning_patterns:
        warnings.filterwarnings('ignore', message=pattern, category=UserWarning)

    logging.getLogger('pyannote.audio').setLevel(logging.ERROR)

    try:
        import torch  # type: ignore

        _log(
            f'  Torch runtime version={torch.__version__} '
            f'cuda_available={torch.cuda.is_available()} '
            f'num_threads={torch.get_num_threads()}'
        )

        nnpack = getattr(torch.backends, 'nnpack', None)
        if nnpack and hasattr(nnpack, 'set_flags'):
            nnpack.set_flags(False)
            _log('  Disabled Torch NNPACK backend for CPU compatibility')
    except Exception:
        pass


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


def _wav_duration_seconds(wav_path: Path) -> float:
    """Return WAV duration in seconds without invoking external tools."""
    with wave.open(str(wav_path), 'rb') as wav:
        frame_count = wav.getnframes()
        frame_rate = wav.getframerate()
        if frame_rate <= 0:
            return 0.0
        return frame_count / float(frame_rate)


def _load_pipeline():
    """Load the pyannote diarization pipeline (cached after first call)."""
    global _pipeline
    if _pipeline is not None:
        return _pipeline

    start = time.monotonic()
    _log(
        f'  Loading PyAnnote model={PYANNOTE_MODEL} cache_dir={PYANNOTE_MODEL_DIR} '
        f'hf_token={"set" if HF_TOKEN else "missing"}'
    )
    _configure_pyannote_runtime()
    from pyannote.audio import Pipeline  # type: ignore

    kwargs = {'cache_dir': PYANNOTE_MODEL_DIR}
    if HF_TOKEN:
        kwargs['use_auth_token'] = HF_TOKEN

    with _heartbeat('PyAnnote model load'):
        _pipeline = Pipeline.from_pretrained(PYANNOTE_MODEL, **kwargs)
    if _pipeline is None:
        raise RuntimeError(
            f'PyAnnote model {PYANNOTE_MODEL} did not load. '
            'Check HF_TOKEN and HuggingFace model access/terms.'
        )
    _log(f'  PyAnnote model loaded in {time.monotonic() - start:.1f}s')
    return _pipeline


def _single_speaker_segments(wav_path: Path) -> list[dict]:
    """Create one speaker segment covering the whole file."""
    duration = round(_wav_duration_seconds(wav_path), 3)
    if duration <= 0:
        return []
    return [{'speaker': 'SPEAKER_00', 'start': 0.0, 'end': duration}]


def diarize_file(wav_path: Path) -> list[dict]:
    """
    Run diarization on a WAV file.

    Returns a list of segments:
        [{'speaker': 'SPEAKER_00', 'start': 0.0, 'end': 2.3}, ...]
    """
    if DIARIZATION_MODE == 'single':
        _log('  Using single-speaker diarization mode')
        return _single_speaker_segments(wav_path)

    if DIARIZATION_MODE != 'pyannote':
        raise ValueError(
            f'Unsupported DIARIZATION_MODE={DIARIZATION_MODE}. '
            'Use "single" or "pyannote".'
        )

    pipeline = _load_pipeline()
    duration = _wav_duration_seconds(wav_path)
    start = time.monotonic()
    wav_size_mb = wav_path.stat().st_size / (1024 * 1024)
    _log(
        f'  Running PyAnnote diarization wav={wav_path.name} '
        f'duration={duration:.1f}s size={wav_size_mb:.2f}MB'
    )
    with _heartbeat(f'PyAnnote inference on {wav_path.name}'):
        diarization = pipeline(str(wav_path))
    segments = [
        {'speaker': label, 'start': round(turn.start, 3), 'end': round(turn.end, 3)}
        for turn, _, label in diarization.itertracks(yield_label=True)
    ]
    speakers = {segment['speaker'] for segment in segments}
    speech_seconds = sum(segment['end'] - segment['start'] for segment in segments)
    _log(
        f'  PyAnnote diarization finished in {time.monotonic() - start:.1f}s '
        f'segments={len(segments)} speakers={len(speakers)} '
        f'speech_seconds={speech_seconds:.1f}'
    )
    if not segments:
        _log('  WARNING PyAnnote returned zero speaker segments')
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
        _log(f'No pending dir for {date} — skipping.')
        return

    aac_files = sorted(pending_dir.glob('*.aac'))
    if not aac_files:
        _log(f'No .aac files found in {pending_dir}')
        return

    archive_dir.mkdir(parents=True, exist_ok=True)
    sample_dir.mkdir(parents=True, exist_ok=True)

    _log(
        f'Processing {len(aac_files)} file(s) for {date} '
        f'mode={DIARIZATION_MODE} pending_dir={pending_dir}'
    )
    if DIARIZATION_MODE == 'pyannote' and not HF_TOKEN:
        _log('WARNING HF_TOKEN is not set; PyAnnote may fail unless the model is cached')

    results: dict[str, dict] = {}
    failed_files: list[str] = []

    with tempfile.TemporaryDirectory() as tmpdir:
        for aac_path in aac_files:
            stem = aac_path.stem
            wav_path = Path(tmpdir) / f'{stem}.wav'
            aac_size_mb = aac_path.stat().st_size / (1024 * 1024)

            _log(f'  Converting {aac_path.name} to WAV size={aac_size_mb:.2f}MB')
            convert_start = time.monotonic()
            try:
                _convert_to_wav(aac_path, wav_path)
            except subprocess.CalledProcessError as exc:
                _log(f'  ERROR converting {aac_path.name}: {exc.stderr.decode()}')
                failed_files.append(aac_path.name)
                continue

            duration = _wav_duration_seconds(wav_path)
            wav_size_mb = wav_path.stat().st_size / (1024 * 1024)
            _log(
                f'  Converted {aac_path.name} in {time.monotonic() - convert_start:.1f}s '
                f'wav_size={wav_size_mb:.2f}MB duration={duration:.1f}s'
            )
            try:
                segments = diarize_file(wav_path)
            except Exception as exc:
                _log(f'  ERROR diarizing {aac_path.name}: {exc}')
                failed_files.append(aac_path.name)
                continue

            # Save sample clips for each unique speaker
            speakers_seen = set()
            sample_paths: dict[str, Optional[str]] = {}
            _log(f'  Extracting speaker samples for {aac_path.name}')
            for seg in segments:
                sp = seg['speaker']
                if sp not in speakers_seen:
                    speakers_seen.add(sp)
                    clip_name = f'{date}_{stem}_{sp}.wav'
                    clip_path = sample_dir / clip_name
                    try:
                        saved = _extract_sample_clip(wav_path, segments, sp, clip_path)
                    except Exception as exc:
                        _log(f'  ERROR extracting sample speaker={sp}: {exc}')
                        saved = None
                    sample_paths[sp] = clip_name if saved else None

            results[aac_path.name] = {
                'segments': segments,
                'sample_paths': sample_paths,
            }
            _log(
                f'  {aac_path.name}: {len(segments)} segment(s), '
                f'{len(speakers_seen)} speaker(s), samples={sample_paths}'
            )

    out_path = archive_dir / 'diarization.json'
    out_path.write_text(json.dumps(results, indent=2))
    _log(f'Written -> {out_path}')

    if failed_files:
        raise RuntimeError(f'Diarization failed for file(s): {", ".join(failed_files)}')


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print('Usage: python diarize.py YYYY-MM-DD')
        sys.exit(1)
    run_diarization(sys.argv[1])
