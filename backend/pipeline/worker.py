"""
Real-time pipeline worker.

Polls the upload_queue table for new uploads (status='received') and
runs the full pipeline (diarize → transcribe → identify → assemble → embed)
for each date that has unprocessed files.

Replaces the cron-based nightly pipeline with near-real-time processing.
"""

import os
import sys
import time
import traceback
from pathlib import Path

from config import AUDIO_PENDING_DIR
from db import execute, fetch_all, get_connection

POLL_INTERVAL: int = int(os.environ.get('WORKER_POLL_INTERVAL', '15'))


def get_pending_uploads() -> list[dict]:
    """Return all uploads with status='received', ordered by time."""
    return fetch_all(
        """
        SELECT id, file_path, recorded_at
        FROM upload_queue
        WHERE status = 'received'
        ORDER BY recorded_at ASC
        """,
    )


def extract_date_from_path(file_path: str) -> str | None:
    """
    Extract the YYYY-MM-DD date from a file path like
    /audio/pending/2026-04-10/qin-f21_1234_abc.aac
    """
    parts = Path(file_path).parts
    for part in parts:
        if len(part) == 10 and part[4] == '-' and part[7] == '-':
            try:
                int(part.replace('-', ''))
                return part
            except ValueError:
                continue
    return None


def process_date(date: str) -> None:
    """Run the full 5-step pipeline for a single date."""
    pending_dir = Path(AUDIO_PENDING_DIR) / date
    if not pending_dir.exists():
        print(f'[worker] No pending dir for {date} — skipping')
        return

    aac_count = len(list(pending_dir.glob('*.aac')))
    if aac_count == 0:
        print(f'[worker] No .aac files for {date} — skipping')
        return

    print(f'[worker] Processing {date} ({aac_count} file(s))')

    from diarize import run_diarization
    from transcribe import run_transcription
    from identify_speakers import run_speaker_id
    from assemble import run_assembly
    from embed import run_embedding

    print(f'[worker]   Step 1/5 — Diarization')
    run_diarization(date)

    print(f'[worker]   Step 2/5 — Transcription')
    run_transcription(date)

    print(f'[worker]   Step 3/5 — Speaker identification')
    run_speaker_id(date)

    print(f'[worker]   Step 4/5 — Assembly')
    run_assembly(date)

    print(f'[worker]   Step 5/5 — Text embedding')
    run_embedding(date)

    print(f'[worker] Pipeline complete for {date}')


def mark_processed(upload_ids: list[str]) -> None:
    """Update status to 'processed' and set processed_at for the given upload IDs."""
    with get_connection() as conn:
        for uid in upload_ids:
            execute(
                """
                UPDATE upload_queue
                SET status = 'processed', processed_at = NOW()
                WHERE id = %s::uuid
                """,
                (uid,),
                conn=conn,
            )


def run_worker() -> None:
    """Main polling loop."""
    print(f'[worker] Starting real-time pipeline worker (poll every {POLL_INTERVAL}s)')
    sys.stdout.flush()

    while True:
        try:
            rows = get_pending_uploads()
            if rows:
                # Group by date
                date_groups: dict[str, list[str]] = {}
                for row in rows:
                    date = extract_date_from_path(row['file_path'])
                    if date is None:
                        # Try to extract from recorded_at
                        ra = row.get('recorded_at')
                        if ra:
                            date = str(ra)[:10]
                    if date:
                        date_groups.setdefault(date, []).append(str(row['id']))

                for date, ids in sorted(date_groups.items()):
                    try:
                        process_date(date)
                        mark_processed(ids)
                        print(f'[worker] Marked {len(ids)} upload(s) as processed for {date}')
                    except Exception:
                        print(f'[worker] ERROR processing {date}:')
                        traceback.print_exc()

                sys.stdout.flush()

        except Exception:
            print('[worker] ERROR in poll loop:')
            traceback.print_exc()
            sys.stdout.flush()

        time.sleep(POLL_INTERVAL)


if __name__ == '__main__':
    run_worker()
