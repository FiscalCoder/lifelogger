ALTER TABLE unknown_speakers
    ADD COLUMN IF NOT EXISTS resolution_kind TEXT,
    ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMPTZ;

UPDATE unknown_speakers
SET resolution_kind = 'person',
    resolved_at = COALESCE(resolved_at, recorded_at)
WHERE resolved = TRUE
  AND resolution_kind IS NULL;

ALTER TABLE transcript_segments
    ADD COLUMN IF NOT EXISTS source_kind TEXT NOT NULL DEFAULT 'unknown_mic',
    ADD COLUMN IF NOT EXISTS exclude_from_rag BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS source_upload_id UUID,
    ADD COLUMN IF NOT EXISTS start_seconds REAL,
    ADD COLUMN IF NOT EXISTS end_seconds REAL;

UPDATE transcript_segments
SET start_seconds = (
        split_part(start_time, ':', 1)::int * 3600
      + split_part(start_time, ':', 2)::int * 60
      + split_part(start_time, ':', 3)::int
    )::real
WHERE start_seconds IS NULL
  AND start_time ~ '^[0-9]{2}:[0-9]{2}:[0-9]{2}$';

UPDATE transcript_segments
SET end_seconds = (
        split_part(end_time, ':', 1)::int * 3600
      + split_part(end_time, ':', 2)::int * 60
      + split_part(end_time, ':', 3)::int
    )::real
WHERE end_seconds IS NULL
  AND end_time ~ '^[0-9]{2}:[0-9]{2}:[0-9]{2}$';

UPDATE transcript_segments ts
SET source_upload_id = (
    SELECT uq.id
    FROM upload_queue uq
    WHERE uq.file_path = ts.source_file
       OR uq.file_path LIKE ('%/' || ts.source_file)
    ORDER BY uq.recorded_at DESC
    LIMIT 1
)
WHERE ts.source_upload_id IS NULL
  AND EXISTS (
    SELECT 1
    FROM upload_queue uq
    WHERE uq.file_path = ts.source_file
       OR uq.file_path LIKE ('%/' || ts.source_file)
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_transcript_segments_source_upload'
    ) THEN
        ALTER TABLE transcript_segments
            ADD CONSTRAINT fk_transcript_segments_source_upload
            FOREIGN KEY (source_upload_id)
            REFERENCES upload_queue(id)
            ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_transcript_segments_rag_source
    ON transcript_segments (source_kind, recorded_at DESC)
    WHERE exclude_from_rag = FALSE;

CREATE INDEX IF NOT EXISTS idx_unknown_speakers_resolution_kind
    ON unknown_speakers (resolution_kind);
