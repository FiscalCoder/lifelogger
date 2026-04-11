# Backend Phases — LifeLogger

Covers all server-side work running on the Hostinger KVM 8 VPS (8 vCPU, 32GB RAM, 400GB NVMe). This includes the ingestion API, nightly processing pipeline, speaker registry, knowledge base, and query API.

All phases assume Ubuntu 22.04 LTS on the VPS. See `infra-setup.md` for environment setup prerequisites.

---

## Phase B1 — FastAPI Ingestion API + File Storage

### Goal
Stand up a production-ready FastAPI application that receives audio chunks from the mobile app, writes them to organized on-disk storage, and exposes the speaker management endpoints needed by the naming UI and nightly pipeline.

### Deliverable
A running FastAPI server (via systemd or tmux initially) that accepts chunk uploads, organizes files by date, and exposes speaker CRUD endpoints. Reachable from the mobile app.

### Tasks

#### B1.1 — Project Structure
```
/app/api/
├── main.py
├── routers/
│   ├── upload.py
│   ├── speakers.py
│   └── audio.py
├── models/
│   ├── chunk.py
│   └── speaker.py
├── db/
│   └── session.py
├── storage/
│   └── fs.py          ← file system helpers
└── requirements.txt
```

#### B1.2 — Dependencies
```
fastapi
uvicorn[standard]
python-multipart       ← for file upload
sqlalchemy
asyncpg                ← async postgres driver
pydantic
aiofiles               ← async file write
```

#### B1.3 — POST /upload Endpoint
- Accept: `multipart/form-data`
- Fields: `device_id` (str), `recorded_at` (ISO8601 str), `duration_seconds` (float), `file` (UploadFile)
- Validation:
  - `device_id` must be non-empty
  - `recorded_at` must be valid ISO8601
  - `duration_seconds` must be > 0.1 and < 3600
  - `file` must be non-empty; content type check (`audio/aac` or `application/octet-stream`)
- Write to: `/audio/pending/<YYYY-MM-DD>/<device_id>_<timestamp>_<uuid>.aac`
  - Date derived from `recorded_at`, not server time
- Insert metadata row into `audio_chunks` table
- Return: `{ "status": "ok", "chunk_id": "<uuid>" }`
- Return 400 on validation failure with descriptive message

#### B1.4 — audio_chunks Table
```sql
CREATE TABLE audio_chunks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id TEXT NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    duration_seconds REAL NOT NULL,
    file_path TEXT NOT NULL,
    received_at TIMESTAMPTZ DEFAULT NOW(),
    processed BOOLEAN DEFAULT FALSE,
    archived BOOLEAN DEFAULT FALSE
);
CREATE INDEX idx_audio_chunks_date ON audio_chunks (DATE(recorded_at));
CREATE INDEX idx_audio_chunks_processed ON audio_chunks (processed) WHERE NOT processed;
```

#### B1.5 — Speaker Management Endpoints
```
GET  /speakers          → list all named speakers (id, name, sample_count, created_at)
GET  /unknowns          → list unresolved unknown speakers from last pipeline run
POST /speakers/name     → { unknown_id: str, name: str } → resolve unknown
DELETE /speakers/:id    → soft delete (set active=false)
GET  /audio/:id/sample  → serve 10s AAC clip for an unknown speaker
```

These endpoints are stubs in B1 — they return empty lists until B4 populates the DB. Define schemas and routes now so F5 can integrate immediately when B4 is done.

#### B1.6 — File Storage Helper
- `storage/fs.py`:
  - `get_pending_dir(date: date) → Path` → creates `/audio/pending/YYYY-MM-DD/` if needed
  - `get_archive_dir(date: date) → Path`
  - `get_sample_path(unknown_id: str) → Path`
  - `write_chunk(upload_file, dest_path) → None` — async streaming write, 64KB chunks

#### B1.7 — Systemd Service
```ini
[Unit]
Description=LifeLogger API
After=network.target postgresql.service

[Service]
User=lifelogger
WorkingDirectory=/app/api
ExecStart=/app/venv/bin/uvicorn main:app --host 0.0.0.0 --port 8000
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

#### B1.8 — Nginx Reverse Proxy
- Proxy `https://your-vps-ip/` → `localhost:8000`
- TLS: Let's Encrypt (certbot) if domain available; self-signed for now
- Set `client_max_body_size 50M` (audio chunks up to ~50MB for long segments)

### Dependencies
- PostgreSQL installed and running
- `/audio/` directory tree exists with correct permissions
- See `infra-setup.md`

### Acceptance Criteria
- [ ] `POST /upload` with valid multipart form → file appears on disk in correct date directory
- [ ] `POST /upload` with invalid payload → 400 with descriptive message
- [ ] All speaker endpoints return valid (empty) JSON
- [ ] API survives restart via systemd
- [ ] Nginx proxies correctly; no 502 errors
- [ ] File upload stress test: 100 concurrent small files — no data corruption

### Estimated Effort
3–4 days

---

## Phase B2 — faster-whisper Transcription Pipeline

### Goal
Implement the nightly transcription step: for each date's pending audio chunks, run faster-whisper large-v3 (int8) transcription, auto-detect language (Tamil/English/mixed), and produce a structured JSON transcript per day.

### Deliverable
A Python script invocable by cron that processes `/audio/pending/` and writes structured JSON transcripts to disk. Chunks moved to `/audio/archive/` after processing.

### Tasks

#### B2.1 — faster-whisper Setup
- Install in venv:
  ```
  pip install faster-whisper
  ```
- Download model:
  ```python
  from faster_whisper import WhisperModel
  model = WhisperModel("large-v3", device="cpu", compute_type="int8")
  ```
- Model stored at `/app/models/faster-whisper-large-v3/`
- First run will download ~3GB; subsequent runs use cache

#### B2.2 — Transcription Script
- `pipeline/transcribe.py`
- Arguments: `--date YYYY-MM-DD` (defaults to yesterday)
- Steps:
  1. Glob all `.aac` files in `/audio/pending/<date>/`
  2. Sort by filename (chronological by timestamp in name)
  3. For each chunk: run `model.transcribe(path, language=None, beam_size=5)`
  4. `language=None` → auto-detect per chunk
  5. Collect segments: `{ start, end, text, language, avg_logprob, no_speech_prob }`
  6. Filter segments where `no_speech_prob > 0.6` (likely silence/noise)
  7. Write per-chunk result to `<date>_<chunk_id>_transcript.json`

#### B2.3 — Parallelization
- Use `concurrent.futures.ProcessPoolExecutor(max_workers=8)`
- Each worker: loads its own model instance (memory: ~2GB per worker × 8 = 16GB; within 32GB)
- Alternative: 4 workers × 2 files each if memory is tight
- Benchmark on actual data; document final worker count in `decisions.md`
- Do NOT share a single model instance across processes (not thread-safe)

#### B2.4 — Day Assembly
- After all chunks transcribed, assemble into a daily transcript:
  ```json
  {
    "date": "2024-01-15",
    "chunks": [
      {
        "chunk_id": "uuid",
        "recorded_at": "2024-01-15T09:32:11Z",
        "duration_seconds": 8.4,
        "language": "ta",
        "segments": [
          { "start": 0.0, "end": 3.2, "text": "...", "speaker": null }
        ]
      }
    ]
  }
  ```
- Speaker field is `null` at this stage (populated by B3)
- Write to `/app/transcripts/<date>.json`

#### B2.5 — Archive Step
- After successful transcription of a chunk, move `.aac` to `/audio/archive/<date>/`
- Update `audio_chunks.archived = true` in DB
- If transcription fails: leave in pending; log error; continue with next chunk

#### B2.6 — Cron Integration
- Add to crontab:
  ```
  0 2 * * * /app/venv/bin/python /app/pipeline/transcribe.py >> /var/log/lifelogger/pipeline.log 2>&1
  ```
- Script should be idempotent: if run twice for same date, skip already-transcribed chunks

#### B2.7 — Quality Metrics
- Log per run: chunk count, total duration, avg transcription time per minute of audio
- Log language distribution: how many chunks detected as `ta` vs `en` vs other
- Alert if > 20% of segments have `no_speech_prob > 0.8` (possible mic/VAD issue)

### Dependencies
- B1 complete (chunks arriving in `/audio/pending/`)
- Python venv with faster-whisper installed
- `/app/models/` directory with model weights

### Acceptance Criteria
- [ ] Script processes all chunks for a given date without error
- [ ] Output JSON is valid and contains correct segment timestamps
- [ ] Tamil speech transcribed in Tamil script; English in Latin script
- [ ] Processed chunks moved to archive; pending dir empty after run
- [ ] Script is idempotent (re-run does not duplicate segments)
- [ ] 8-core parallelization demonstrably faster than single-process (benchmark logged)

### Estimated Effort
4–5 days

---

## Phase B3 — pyannote Diarization Integration

### Goal
Add speaker diarization as the first step in the nightly pipeline. Before transcription, identify who is speaking when using pyannote-audio. Annotate transcript segments with anonymous speaker labels (SPEAKER_00, SPEAKER_01, ...) that feed into the ECAPA speaker ID step.

### Deliverable
The nightly pipeline produces transcripts with per-segment `speaker` fields populated with anonymous labels. Diarization boundaries improve transcription by chunking per speaker turn.

### Tasks

#### B3.1 — pyannote Setup
- Install:
  ```
  pip install pyannote.audio
  ```
- Obtain HuggingFace token and accept pyannote terms of service
- Download `pyannote/speaker-diarization-3.1` pipeline
- Store in `/app/models/pyannote/`
- Test on a sample audio file before pipeline integration

#### B3.2 — Diarization Script
- `pipeline/diarize.py`
- Input: path to `.aac` file
- Output: list of `{ start: float, end: float, speaker: str }` dicts
  - e.g., `[{ "start": 0.0, "end": 2.3, "speaker": "SPEAKER_00" }, ...]`
- Implementation:
  ```python
  from pyannote.audio import Pipeline
  pipeline = Pipeline.from_pretrained("pyannote/speaker-diarization-3.1", use_auth_token=TOKEN)
  diarization = pipeline(audio_path)
  segments = [{"start": turn.start, "end": turn.end, "speaker": label}
              for turn, _, label in diarization.itertracks(yield_label=True)]
  ```
- Handle mono requirement: convert if needed (`ffmpeg -ac 1`)

#### B3.3 — Audio Format Normalization
- pyannote expects WAV; input is AAC
- Convert in-place before diarization:
  ```python
  subprocess.run(["ffmpeg", "-i", aac_path, "-ar", "16000", "-ac", "1", wav_path])
  ```
- Reuse wav for both diarization and transcription; delete wav after processing

#### B3.4 — Pipeline Integration
- Insert diarization step before transcription in `pipeline/run.py`
- For each chunk:
  1. Convert AAC → WAV
  2. Diarize WAV → speaker segments
  3. Split WAV by speaker segment (optional optimization: transcribe per-segment for better accuracy)
  4. Transcribe each segment with `initial_prompt` derived from language guess
  5. Merge: assign speaker label to each Whisper output segment by timestamp overlap

#### B3.5 — Speaker Segment Extraction for ECAPA
- For each unique speaker label per chunk, extract the longest 10-second contiguous segment
- Save as `/audio/samples/<date>_<chunk_id>_<speaker_label>.wav`
- These become the audio samples for the naming UI and ECAPA enrollment

#### B3.6 — Updated Transcript Schema
```json
{
  "date": "...",
  "chunks": [{
    "chunk_id": "...",
    "segments": [
      {
        "start": 0.0, "end": 2.3,
        "speaker": "SPEAKER_00",
        "text": "...",
        "language": "ta"
      }
    ]
  }]
}
```

#### B3.7 — Performance Considerations
- pyannote diarization on 8 vCPU CPU is slower than GPU; expect 0.3–0.5× real-time
- For 8hr of audio at 50% speech activity = ~4hr audio → ~2–3hr diarization
- Run diarization sequentially; transcription in parallel (transcription is the bottleneck)
- If cron window too tight: diarize and transcribe previous day at 2AM, not same day

### Dependencies
- B2 complete (transcription infrastructure exists)
- HuggingFace account + pyannote model access token

### Acceptance Criteria
- [ ] Diarization produces valid speaker segments for a test audio file
- [ ] Transcript segments have non-null `speaker` field (SPEAKER_XX)
- [ ] Speaker sample audio clips saved correctly for ECAPA input
- [ ] Pipeline runs within 2AM–6AM window for a full day's audio
- [ ] Diarization is skipped (with warning) if pyannote model unavailable

### Estimated Effort
4–5 days

---

## Phase B4 — SpeechBrain ECAPA Speaker Registry

### Goal
Implement persistent speaker identification using ECAPA-TDNN embeddings stored in pgvector. Match each anonymous diarized speaker against the registry; if matched, apply the known name; if unmatched, create an `unknown_speakers` record for the naming UI.

### Deliverable
Named speakers are automatically identified in transcripts. Unknown speakers appear in the naming UI with audio samples. Naming a speaker updates their profile and is remembered for all future runs.

### Tasks

#### B4.1 — SpeechBrain Setup
- Install:
  ```
  pip install speechbrain
  ```
- Model: `speechbrain/spkrec-ecapa-voxceleb` (192-dim ECAPA-TDNN)
- Download on first run:
  ```python
  from speechbrain.inference.speaker import EncoderClassifier
  classifier = EncoderClassifier.from_hparams(
      source="speechbrain/spkrec-ecapa-voxceleb",
      savedir="/app/models/speechbrain-ecapa/"
  )
  ```
- Test: produce embedding from a 5s audio clip

#### B4.2 — DB Schema (speaker_profiles + unknown_speakers)
```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE speaker_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL UNIQUE,
    embedding VECTOR(192) NOT NULL,
    sample_count INTEGER DEFAULT 1,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    active BOOLEAN DEFAULT TRUE
);

CREATE TABLE unknown_speakers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    temp_name TEXT NOT NULL,           -- e.g. SPEAKER_00_20240115
    embedding VECTOR(192) NOT NULL,
    audio_sample_path TEXT,            -- path to 10s wav clip
    recorded_at DATE NOT NULL,
    source_chunk_id UUID REFERENCES audio_chunks(id),
    resolved BOOLEAN DEFAULT FALSE,
    resolved_as UUID REFERENCES speaker_profiles(id),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_speaker_profiles_embedding
    ON speaker_profiles USING ivfflat (embedding vector_cosine_ops) WITH (lists = 10);

CREATE INDEX idx_unknown_speakers_unresolved
    ON unknown_speakers (resolved) WHERE NOT resolved;
```

#### B4.3 — Embedding Extraction
- `pipeline/embed_speaker.py`
- Input: wav file path (10s clip from B3 step)
- Output: numpy array shape `(192,)`
- Implementation:
  ```python
  import torchaudio
  signal, fs = torchaudio.load(wav_path)
  embedding = classifier.encode_batch(signal)
  return embedding.squeeze().numpy()
  ```
- Normalize embedding to unit length before storage (cosine similarity requires this)

#### B4.4 — Registry Lookup
- `pipeline/speaker_registry.py`
- `lookup(embedding: np.ndarray) → Optional[SpeakerProfile]`:
  ```sql
  SELECT id, name, embedding <=> $1::vector AS distance
  FROM speaker_profiles
  WHERE active = TRUE
  ORDER BY distance ASC
  LIMIT 1;
  ```
  - If `distance < (1 - 0.82) = 0.18` (cosine distance, lower = more similar): return match
  - Else: return None

- `enroll(name: str, embedding: np.ndarray) → SpeakerProfile`: insert new row
- `update_embedding(profile_id, new_embedding)`: incremental averaging
  ```python
  updated = (old_embedding * sample_count + new_embedding) / (sample_count + 1)
  ```

#### B4.5 — Pipeline Integration
- For each speaker segment in day's transcript:
  1. Extract embedding from audio sample
  2. Call `lookup(embedding)`
  3. If match: annotate segment with speaker name, call `update_embedding()`
  4. If no match: create `unknown_speakers` record; annotate segment with temp name
- Update transcript JSON with resolved names where available

#### B4.6 — Enrollment (Initial Self-Enrollment)
- One-time script: `pipeline/enroll.py --name "Bharghav" --audio <path_to_5min_recording.wav>`
- Extracts multiple 10s clips → averages embeddings → inserts into `speaker_profiles`
- Run this before first real nightly run so your own voice is recognized immediately

#### B4.7 — Naming UI Integration
- `/unknowns` endpoint now returns real records from `unknown_speakers` table
- `/speakers/name` route: 
  1. Look up unknown by ID
  2. Check if name already exists in `speaker_profiles`
  3. If yes: merge (average) embedding into existing profile
  4. If no: create new `speaker_profiles` entry
  5. Set `unknown_speakers.resolved = true`, `resolved_as = profile_id`
  6. Write reprocess flag

### Dependencies
- B3 complete (speaker segments + sample clips exist)
- pgvector installed in PostgreSQL
- B1 speaker endpoints wired to real DB

### Acceptance Criteria
- [ ] Your own enrolled voice is correctly matched in nightly run (cosine similarity > 0.82)
- [ ] Unknown speakers appear in `/unknowns` with audio sample paths
- [ ] Naming a speaker via `/speakers/name` persists across runs
- [ ] Embedding averaging improves match rate over multiple days
- [ ] ivfflat index does not degrade lookup speed as profile count grows to 50+

### Estimated Effort
5–6 days

---

## Phase B5 — pgvector Knowledge Base + Embeddings

### Goal
Embed every resolved transcript segment using a multilingual sentence transformer and store in pgvector. This forms the queryable personal knowledge base.

### Deliverable
All transcribed segments are embedded and searchable by semantic similarity. A day's worth of conversation is fully indexed by the morning after.

### Tasks

#### B5.1 — Embedding Model Setup
- Install:
  ```
  pip install sentence-transformers
  ```
- Model: `sentence-transformers/paraphrase-multilingual-mpnet-base-v2`
  - 768-dim output
  - Handles Tamil, English, code-switching natively
  - ~1GB model size
- Download on first run:
  ```python
  from sentence_transformers import SentenceTransformer
  model = SentenceTransformer("paraphrase-multilingual-mpnet-base-v2")
  model.save("/app/models/multilingual-mpnet/")
  ```

#### B5.2 — DB Schema (transcript_segments)
```sql
CREATE TABLE transcript_segments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    speaker_id UUID REFERENCES speaker_profiles(id),
    speaker_name TEXT,                     -- denormalized for query convenience
    text TEXT NOT NULL,
    language TEXT,                         -- 'ta', 'en', 'mixed'
    embedding VECTOR(768) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    source_chunk_id UUID REFERENCES audio_chunks(id),
    source_file TEXT,
    segment_start REAL,                    -- seconds within source chunk
    segment_end REAL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_transcript_segments_embedding
    ON transcript_segments USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

CREATE INDEX idx_transcript_segments_speaker
    ON transcript_segments (speaker_id);

CREATE INDEX idx_transcript_segments_date
    ON transcript_segments (DATE(recorded_at));
```

#### B5.3 — Embedding Pipeline
- `pipeline/embed_segments.py`
- Input: assembled transcript JSON for a date
- For each segment:
  1. Skip if `text` is empty or < 3 words
  2. Compute `embedding = model.encode(segment.text, normalize_embeddings=True)`
  3. Insert into `transcript_segments`
- Batch encode: `model.encode(texts, batch_size=64)` — more efficient than per-segment
- Mark `audio_chunks.processed = true` after successful embedding

#### B5.4 — Language Tag Normalization
- Whisper outputs language codes like `"ta"`, `"en"`, `None`
- If `None` and text contains Tamil Unicode range (U+0B80–U+0BFF): tag as `"ta"`
- If both scripts present in same segment: tag as `"mixed"`

#### B5.5 — Deduplication Guard
- Before insert, check if segment already exists:
  ```sql
  SELECT id FROM transcript_segments
  WHERE source_chunk_id = $1 AND ABS(segment_start - $2) < 0.1
  LIMIT 1;
  ```
- If exists: skip (idempotent pipeline)

#### B5.6 — Pipeline Ordering
- Full nightly order:
  1. `diarize.py` — produces speaker segments + sample clips
  2. `transcribe.py` — produces text segments
  3. `identify_speakers.py` — matches embeddings against registry
  4. `embed_segments.py` — embeds text and inserts into knowledge base
- Coordinate via `pipeline/run.py` which calls each step in sequence

### Dependencies
- B4 complete (speaker names resolved before embedding)
- pgvector schema for `transcript_segments` created

### Acceptance Criteria
- [ ] All segments from a sample day are embedded and queryable
- [ ] Tamil segments return Tamil-language results in semantic search
- [ ] Embedding step is idempotent (re-run same day → no duplicates)
- [ ] Segments with resolved speaker names have correct `speaker_name` populated
- [ ] Index creation completes in < 30s for 10,000 segments

### Estimated Effort
3–4 days

---

## Phase B6 — Semantic Query API

### Goal
Expose a query endpoint that accepts natural language queries and returns the most relevant transcript segments, ranked by semantic similarity, with speaker and time metadata.

### Deliverable
`GET /query?q=<text>&speaker=<name>&date_from=<date>&date_to=<date>` returns ranked transcript segments. The personal knowledge base is fully queryable.

### Tasks

#### B6.1 — Query Endpoint
- `GET /query`
- Parameters: `q` (required), `speaker` (optional name filter), `date_from`, `date_to`, `limit` (default 10)
- Implementation:
  1. Embed query: `model.encode(q, normalize_embeddings=True)`
  2. Run pgvector similarity search:
     ```sql
     SELECT id, speaker_name, text, language, recorded_at, segment_start,
            1 - (embedding <=> $1::vector) AS similarity
     FROM transcript_segments
     WHERE (speaker_name = $2 OR $2 IS NULL)
       AND (recorded_at >= $3 OR $3 IS NULL)
       AND (recorded_at <= $4 OR $4 IS NULL)
     ORDER BY embedding <=> $1::vector ASC
     LIMIT $5;
     ```
  3. Return results with similarity score

#### B6.2 — Response Schema
```json
{
  "query": "What did I discuss with Karthik last week?",
  "results": [
    {
      "id": "uuid",
      "speaker": "Karthik",
      "text": "...",
      "language": "ta",
      "recorded_at": "2024-01-10T14:23:00Z",
      "similarity": 0.87,
      "audio_ref": "/audio/chunks/<chunk_id>"
    }
  ],
  "total": 10
}
```

#### B6.3 — Speaker Name Filter
- Accept speaker name string as query param
- Join to `speaker_profiles` to get speaker_id, then filter
- If speaker not found: return 404 with message "No speaker named X in registry"

#### B6.4 — Query Caching (Optional Optimization)
- Cache recent query embeddings in memory (LRU, max 100 entries)
- Same query text → same embedding; skip recompute
- Not required for MVP; implement if query latency > 2s

#### B6.5 — Minimal Query UI (Optional)
- Extend `naming.html` with a search box at top of page
- On submit: `fetch('/query?q=<text>')` → display results below
- Or document as: use `curl` or any REST client for now

#### B6.6 — Stats Endpoint
- `GET /stats` → overview:
  ```json
  {
    "total_segments": 48200,
    "total_speakers": 12,
    "date_range": { "from": "2024-01-01", "to": "2024-01-15" },
    "language_breakdown": { "ta": 0.62, "en": 0.31, "mixed": 0.07 }
  }
  ```
- Useful for monitoring knowledge base growth

### Dependencies
- B5 complete (segments embedded in DB)
- Multilingual embedding model loaded in API process

### Acceptance Criteria
- [ ] `GET /query?q=meeting` returns relevant segments within 2s
- [ ] Speaker filter returns only segments from that speaker
- [ ] Date filter correctly narrows results
- [ ] Results include similarity scores and are ranked correctly
- [ ] Tamil query returns Tamil segments ranked above English (if query is in Tamil)

### Estimated Effort
3 days
