-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Enable uuid generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Known, named speakers
CREATE TABLE IF NOT EXISTS speaker_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL UNIQUE,
    embedding VECTOR(192),
    sample_count INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ivfflat index for fast cosine similarity search on speaker embeddings
-- lists=10 is appropriate for a small table (< 1000 speakers)
CREATE INDEX IF NOT EXISTS idx_speaker_profiles_embedding
    ON speaker_profiles USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 10);

-- Unknown speakers awaiting naming
CREATE TABLE IF NOT EXISTS unknown_speakers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    temp_name TEXT NOT NULL,
    embedding VECTOR(192),
    audio_sample TEXT,
    recorded_at TIMESTAMPTZ NOT NULL,
    resolved BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_unknown_speakers_unresolved
    ON unknown_speakers (resolved)
    WHERE NOT resolved;

-- Final assembled transcript segments
CREATE TABLE IF NOT EXISTS transcript_segments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    speaker TEXT NOT NULL,
    text TEXT NOT NULL,
    language TEXT,
    embedding VECTOR(768),
    recorded_at TIMESTAMPTZ NOT NULL,
    source_file TEXT NOT NULL,
    start_time TEXT,
    end_time TEXT
);

-- ivfflat index for semantic search over transcript embeddings
-- lists=100 is appropriate for a larger table (up to ~1M segments)
CREATE INDEX IF NOT EXISTS idx_transcript_segments_embedding
    ON transcript_segments USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

CREATE INDEX IF NOT EXISTS idx_transcript_segments_recorded_at
    ON transcript_segments (recorded_at DESC);

CREATE INDEX IF NOT EXISTS idx_transcript_segments_speaker
    ON transcript_segments (speaker);

-- Upload queue for tracking chunks received from mobile
CREATE TABLE IF NOT EXISTS upload_queue (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id TEXT NOT NULL,
    file_path TEXT NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    duration_seconds REAL,
    processed_at TIMESTAMPTZ,
    status TEXT NOT NULL DEFAULT 'received'
);

CREATE INDEX IF NOT EXISTS idx_upload_queue_status
    ON upload_queue (status)
    WHERE status != 'done';

CREATE INDEX IF NOT EXISTS idx_upload_queue_recorded_at
    ON upload_queue (recorded_at DESC);
