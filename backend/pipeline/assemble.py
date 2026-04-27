"""
Step 4 of the nightly pipeline: assemble diarization + transcription +
speaker IDs into final TranscriptSegments and insert into the DB.

Usage:
    python assemble.py 2026-04-10

Reads:  AUDIO_ARCHIVE_DIR/<date>/diarization.json
        AUDIO_ARCHIVE_DIR/<date>/transcription.json
        AUDIO_ARCHIVE_DIR/<date>/speaker_ids.json
Writes: AUDIO_ARCHIVE_DIR/<date>/transcript.json
        DB: transcript_segments (new rows)
"""

import json
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

from config import AUDIO_ARCHIVE_DIR
from db import execute, fetch_one, get_connection


def _recorded_at_from_filename(filename: str, segment_start: float, date: str) -> str:
    """
    Derive recorded_at from the filename timestamp + segment start offset.

    Filename format: <deviceId>_<timestampMs>_<suffix>.aac
    Falls back to midnight of *date* if the filename doesn't match.
    """
    try:
        stem = Path(filename).stem          # e.g. qin-f21_1775799262574_1lnqxw
        ts_ms = int(stem.rsplit('_', 2)[-2])
        ts_sec = ts_ms / 1000.0 + segment_start
        return datetime.fromtimestamp(ts_sec, tz=timezone.utc).isoformat()
    except (ValueError, IndexError):
        return datetime(*[int(p) for p in date.split('-')], tzinfo=timezone.utc).isoformat()


def _format_time(seconds: float) -> str:
    """Convert fractional seconds to HH:MM:SS string."""
    total = int(seconds)
    h = total // 3600
    m = (total % 3600) // 60
    s = total % 60
    return f'{h:02d}:{m:02d}:{s:02d}'


def _resolve_speaker(
    filename: str,
    speaker_label: str,
    speaker_ids: dict,
) -> str:
    """Return the resolved name (or temp_name) for a diarization speaker label."""
    key = f'{filename}::{speaker_label}'
    info = speaker_ids.get(key, {})
    if info.get('resolved'):
        return info['name']
    return info.get('temp_name') or speaker_label


def _segment_exists(source_file: str, start_seconds: float, start_time: str, conn) -> bool:
    """Idempotency guard: returns True if segment already in DB."""
    row = fetch_one(
        """
        SELECT id FROM transcript_segments
        WHERE source_file = %s
          AND (
              ABS(start_seconds - %s) < 0.001
              OR (start_seconds IS NULL AND start_time = %s)
          )
        LIMIT 1
        """,
        (source_file, start_seconds, start_time),
        conn=conn,
    )
    return row is not None


def _lookup_source_upload_id(filename: str, conn) -> Optional[str]:
    """Return upload_queue.id for the source audio file when available."""
    row = fetch_one(
        """
        SELECT id
        FROM upload_queue
        WHERE file_path = %s
           OR file_path LIKE %s
        ORDER BY recorded_at DESC
        LIMIT 1
        """,
        (filename, f'%/{filename}'),
        conn=conn,
    )
    return str(row['id']) if row else None


def assemble_day(date: str) -> list[dict]:
    """
    Merge pipeline outputs for *date* into a list of TranscriptSegment dicts.
    Inserts new rows into transcript_segments and returns the assembled list.
    """
    archive_dir = Path(AUDIO_ARCHIVE_DIR) / date

    # Load intermediate outputs
    diar_path = archive_dir / 'diarization.json'
    trans_path = archive_dir / 'transcription.json'
    spk_path = archive_dir / 'speaker_ids.json'

    if not diar_path.exists():
        print(f'[assemble] diarization.json missing for {date}')
        return []
    if not trans_path.exists():
        print(f'[assemble] transcription.json missing for {date}')
        return []
    if not spk_path.exists():
        print(f'[assemble] speaker_ids.json missing for {date}')
        return []

    diarization: dict = json.loads(diar_path.read_text())
    transcription: dict = json.loads(trans_path.read_text())
    speaker_ids: dict = json.loads(spk_path.read_text())

    assembled: list[dict] = []
    inserted = 0
    skipped = 0

    with get_connection() as conn:
        for filename in diarization:
            trans_segments: list[dict] = transcription.get(filename, [])
            source_upload_id = _lookup_source_upload_id(filename, conn)

            for seg in trans_segments:
                speaker_label = seg.get('speaker', 'UNKNOWN')
                speaker_name = _resolve_speaker(filename, speaker_label, speaker_ids)
                start_seconds = float(seg.get('start', 0.0))
                end_seconds = float(seg.get('end', 0.0))
                start_time = _format_time(start_seconds)
                end_time = _format_time(end_seconds)
                text = seg.get('text', '').strip()
                language = seg.get('language')

                if not text:
                    continue

                recorded_at = _recorded_at_from_filename(
                    filename, start_seconds, date
                )

                segment_dict = {
                    'speaker': speaker_name,
                    'text': text,
                    'language': language,
                    'recordedAt': recorded_at,
                    'sourceFile': filename,
                    'startTime': start_time,
                    'endTime': end_time,
                    'sourceUploadId': source_upload_id,
                    'startSeconds': start_seconds,
                    'endSeconds': end_seconds,
                    'sourceKind': 'unknown_mic',
                    'excludeFromRag': False,
                }
                assembled.append(segment_dict)

                # Idempotency: skip if already in DB
                if _segment_exists(filename, start_seconds, start_time, conn):
                    skipped += 1
                    continue

                execute(
                    """
                    INSERT INTO transcript_segments
                        (
                            speaker, text, language, recorded_at, source_file,
                            start_time, end_time, source_upload_id,
                            start_seconds, end_seconds, source_kind, exclude_from_rag
                        )
                    VALUES (
                        %s, %s, %s, %s::timestamptz, %s,
                        %s, %s, %s::uuid,
                        %s, %s, %s, %s
                    )
                    """,
                    (
                        speaker_name,
                        text,
                        language,
                        recorded_at,
                        filename,
                        start_time,
                        end_time,
                        source_upload_id,
                        start_seconds,
                        end_seconds,
                        'unknown_mic',
                        False,
                    ),
                    conn=conn,
                )
                inserted += 1

    print(f'[assemble] {date}: {inserted} segment(s) inserted, {skipped} skipped (already exist)')

    # Write assembled transcript JSON
    transcript = {'date': date, 'segments': assembled}
    out_path = archive_dir / 'transcript.json'
    out_path.write_text(json.dumps(transcript, indent=2, ensure_ascii=False))
    print(f'[assemble] Written → {out_path}')

    return assembled


def run_assembly(date: str) -> None:
    assemble_day(date)


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print('Usage: python assemble.py YYYY-MM-DD')
        sys.exit(1)
    run_assembly(sys.argv[1])
