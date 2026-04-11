#!/bin/bash
# Entry script called by cron at 02:00 every night.
# Processes all pending audio for the previous calendar day.
# Can also be run manually: bash run_pipeline.sh 2026-04-10

set -euo pipefail

DATE="${1:-$(date -d 'yesterday' +%Y-%m-%d 2>/dev/null || date -v-1d +%Y-%m-%d)}"
PENDING_DIR="${AUDIO_PENDING_DIR:-/audio/pending}/$DATE"

echo "[pipeline] ===== Starting pipeline for $DATE ====="

if [ ! -d "$PENDING_DIR" ]; then
    echo "[pipeline] No pending audio directory found at $PENDING_DIR — nothing to do."
    exit 0
fi

FILE_COUNT=$(find "$PENDING_DIR" -name '*.aac' | wc -l)
if [ "$FILE_COUNT" -eq 0 ]; then
    echo "[pipeline] No .aac files found in $PENDING_DIR — nothing to do."
    exit 0
fi

echo "[pipeline] Found $FILE_COUNT audio file(s) to process."

cd /app

echo "[pipeline] Step 1/5 — Diarization"
python diarize.py "$DATE"

echo "[pipeline] Step 2/5 — Transcription"
python transcribe.py "$DATE"

echo "[pipeline] Step 3/5 — Speaker identification"
python identify_speakers.py "$DATE"

echo "[pipeline] Step 4/5 — Assembly"
python assemble.py "$DATE"

echo "[pipeline] Step 5/5 — Text embedding"
python embed.py "$DATE"

echo "[pipeline] ===== Pipeline complete for $DATE ====="
