# Infrastructure Setup — VPS (Hostinger KVM 8)

This guide covers everything needed to prepare the VPS before any application code is deployed. All commands assume Ubuntu 22.04 LTS with root or sudo access.

---

## 1. System User + Base Packages

```bash
# Create a dedicated user
adduser lifelogger
usermod -aG sudo lifelogger

# Update system
apt update && apt upgrade -y

# Install base tools
apt install -y \
    build-essential \
    curl \
    git \
    vim \
    htop \
    ffmpeg \
    tmux \
    net-tools \
    lsof \
    unzip \
    software-properties-common \
    ca-certificates

# Install Python 3.11
apt install -y python3.11 python3.11-venv python3.11-dev python3-pip
update-alternatives --install /usr/bin/python3 python3 /usr/bin/python3.11 1
```

---

## 2. Cloud PostgreSQL Database

LifeLogger uses the AWS RDS PostgreSQL database configured in `backend/.env`.
Do not install or run a separate PostgreSQL server on the VPS for the normal
cloud deployment path.

Install only the PostgreSQL client so the VPS can run checks and one-off SQL:

```bash
apt update
apt install -y postgresql-client
```

The application connection string is:

```env
DATABASE_URL="postgres://lifelogger:<URL_ENCODED_PASSWORD>@lifelogger.c3ueqi6k0dik.ap-south-1.rds.amazonaws.com:5432/lifelogger?sslmode=require"
```

The password must be URL-encoded inside `DATABASE_URL`.

---

## 3. Database Initialization

The RDS database is initialized by repo migrations, not by Docker volume startup
scripts and not by local PostgreSQL setup.

```bash
cd /app/lifelogger/backend/api
npm ci
npm run db:init
```

This applies `src/db/migrations/0001_init.sql`, including:

- `CREATE EXTENSION IF NOT EXISTS vector`
- `CREATE EXTENSION IF NOT EXISTS "uuid-ossp"`
- `CREATE EXTENSION IF NOT EXISTS pgcrypto`
- application tables
- vector and lookup indexes
- migration tracking in `schema_migrations`

See `docs/db-workflow.md` for schema changes and versioned data changes.

---

## 4. Database Verification

```bash
cd /app/lifelogger/backend
set -a
source .env
set +a

psql "$DATABASE_URL" -c "
SELECT current_database();
SELECT extname, extversion FROM pg_extension WHERE extname IN ('vector', 'uuid-ossp', 'pgcrypto');
SELECT id, applied_at FROM schema_migrations ORDER BY id;
"
```

---

## 5. Directory Structure

```bash
# Create audio storage tree
mkdir -p /audio/pending
mkdir -p /audio/archive
mkdir -p /audio/samples

# Set ownership
chown -R lifelogger:lifelogger /audio
chmod -R 750 /audio

# Create app directory
mkdir -p /app/api
mkdir -p /app/pipeline
mkdir -p /app/models
mkdir -p /app/transcripts

chown -R lifelogger:lifelogger /app

# Create log directory
mkdir -p /var/log/lifelogger
chown lifelogger:lifelogger /var/log/lifelogger
```

### Storage Budget Estimate

| Directory | Estimated Size | Notes |
|---|---|---|
| `/audio/pending/` | 1–5 GB/day | Raw AAC chunks before processing |
| `/audio/archive/` | ~400 GB over months | Accumulates indefinitely |
| `/audio/samples/` | < 500 MB | 10s clips for naming UI |
| `/app/models/` | ~15 GB | faster-whisper (3GB) + pyannote (1GB) + SpeechBrain (1GB) + mpnet (1GB) |
| `/app/transcripts/` | ~50 MB/day | JSON transcript files |

400GB NVMe: audio archive will last ~80 days at 5GB/day. Plan for periodic cleanup of old archives or add storage.

---

## 6. Python Virtual Environment

```bash
# Create venv as lifelogger user
su - lifelogger
cd /app
python3.11 -m venv venv
source venv/bin/activate

# Install core dependencies
pip install --upgrade pip wheel setuptools

pip install \
    fastapi \
    "uvicorn[standard]" \
    python-multipart \
    sqlalchemy \
    asyncpg \
    alembic \
    pydantic \
    aiofiles \
    jinja2 \
    faster-whisper \
    speechbrain \
    sentence-transformers \
    pgvector \
    torch --index-url https://download.pytorch.org/whl/cpu \
    torchaudio --index-url https://download.pytorch.org/whl/cpu

# pyannote (requires huggingface_hub)
pip install pyannote.audio huggingface_hub

# Freeze
pip freeze > /app/requirements.txt
```

### PyTorch Note
Install the CPU-only build of PyTorch. The VPS has no GPU; the full CUDA build is ~3GB larger and provides no benefit here.

```bash
# Verify CPU-only torch installed
python -c "import torch; print(torch.__version__, torch.cuda.is_available())"
# Expected: 2.x.x False
```

---

## 7. Model Pre-Download Script

Run once on the VPS to download all models before the first nightly run:

```bash
# /app/pipeline/download_models.py

from faster_whisper import WhisperModel
from speechbrain.inference.speaker import EncoderClassifier
from sentence_transformers import SentenceTransformer
from pyannote.audio import Pipeline
import os

HF_TOKEN = os.environ["HF_TOKEN"]   # set in environment, never hardcoded

print("Downloading faster-whisper large-v3...")
WhisperModel("large-v3", device="cpu", compute_type="int8",
             download_root="/app/models/faster-whisper/")

print("Downloading SpeechBrain ECAPA-TDNN...")
EncoderClassifier.from_hparams(
    source="speechbrain/spkrec-ecapa-voxceleb",
    savedir="/app/models/speechbrain-ecapa/"
)

print("Downloading paraphrase-multilingual-mpnet-base-v2...")
SentenceTransformer("paraphrase-multilingual-mpnet-base-v2").save(
    "/app/models/multilingual-mpnet/"
)

print("Downloading pyannote speaker-diarization-3.1...")
Pipeline.from_pretrained("pyannote/speaker-diarization-3.1",
                         use_auth_token=HF_TOKEN,
                         cache_dir="/app/models/pyannote/")

print("All models downloaded.")
```

Run as:
```bash
HF_TOKEN=hf_xxx python /app/pipeline/download_models.py
```

---

## 8. Cron Job Setup

```bash
# Edit crontab for lifelogger user
crontab -u lifelogger -e

# Add:
# Run nightly pipeline at 2AM
0 2 * * * /app/venv/bin/python /app/pipeline/run.py >> /var/log/lifelogger/pipeline.log 2>&1

# Weekly: clean up processed wav temp files
0 3 * * 0 find /tmp -name "*.wav" -mtime +1 -delete >> /var/log/lifelogger/cleanup.log 2>&1
```

### Pipeline Script Entry Point

```bash
# /app/pipeline/run.py (skeleton)
import sys
from datetime import date, timedelta

target_date = sys.argv[1] if len(sys.argv) > 1 else str(date.today() - timedelta(days=1))

print(f"[pipeline] Starting for date: {target_date}")

from diarize import run_diarization
from transcribe import run_transcription
from identify_speakers import run_speaker_id
from embed_segments import run_embedding

run_diarization(target_date)
run_transcription(target_date)
run_speaker_id(target_date)
run_embedding(target_date)

print("[pipeline] Complete.")
```

Manual run for a specific date:
```bash
/app/venv/bin/python /app/pipeline/run.py 2024-01-15
```

---

## 9. Nginx + API Service

### Nginx Config

```nginx
# /etc/nginx/sites-available/lifelogger
server {
    listen 80;
    server_name _;

    client_max_body_size 50M;

    location / {
        proxy_pass http://127.0.0.1:8000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_read_timeout 120s;
    }

    location /audio/ {
        alias /audio/;
        internal;   # only accessible via X-Accel-Redirect from the API
    }
}
```

```bash
apt install -y nginx
ln -s /etc/nginx/sites-available/lifelogger /etc/nginx/sites-enabled/
rm /etc/nginx/sites-enabled/default
nginx -t && systemctl reload nginx
```

### Systemd Service for API

```ini
# /etc/systemd/system/lifelogger-api.service
[Unit]
Description=LifeLogger API
After=network.target

[Service]
User=lifelogger
Group=lifelogger
WorkingDirectory=/app/lifelogger/backend/api
EnvironmentFile=/app/lifelogger/backend/.env
Environment="AUDIO_PENDING_DIR=/audio/pending"
Environment="AUDIO_ARCHIVE_DIR=/audio/archive"
Environment="AUDIO_SAMPLE_DIR=/audio/samples"
Environment="PORT=8000"
ExecStart=/usr/bin/npm run start
Restart=always
RestartSec=5
StandardOutput=append:/var/log/lifelogger/api.log
StandardError=append:/var/log/lifelogger/api.log

[Install]
WantedBy=multi-user.target
```

```bash
systemctl daemon-reload
systemctl enable lifelogger-api
systemctl start lifelogger-api
systemctl status lifelogger-api
```

---

## 10. Firewall

```bash
ufw allow ssh
ufw allow 80/tcp
ufw allow 443/tcp    # for future TLS
ufw enable
ufw status
```

Do NOT expose port 8000 directly. Only Nginx (80/443) should be public-facing.

---

## 11. Environment Variables

Store secrets in `backend/.env` (readable only by lifelogger on the VPS):

```bash
touch /app/lifelogger/backend/.env
chmod 600 /app/lifelogger/backend/.env
chown lifelogger:lifelogger /app/lifelogger/backend/.env
```

```env
# /app/lifelogger/backend/.env
DATABASE_URL="postgres://lifelogger:<URL_ENCODED_PASSWORD>@lifelogger.c3ueqi6k0dik.ap-south-1.rds.amazonaws.com:5432/lifelogger?sslmode=require"
API_TOKEN=replace_with_a_strong_random_token
HF_TOKEN=hf_xxx
SPEAKER_MATCH_THRESHOLD=0.82
DIARIZATION_MODE=pyannote
DIARIZATION_HEARTBEAT_SECONDS=30
MAX_WORKERS=2
OMP_NUM_THREADS=2
MKL_NUM_THREADS=2
OPENBLAS_NUM_THREADS=2
```

`DIARIZATION_MODE=pyannote` enables real multi-speaker diarization. Keep
`HF_TOKEN` set and accepted for `pyannote/speaker-diarization-3.1`.
`DIARIZATION_HEARTBEAT_SECONDS` controls progress logs while model loading or
inference is running.

The API service reads this file with `EnvironmentFile`. Docker Compose reads it
with `env_file`.

All API routes except `/health` require bearer-token auth:

```bash
curl -H "Authorization: Bearer $API_TOKEN" \
  https://lifelogger.huecentral.cloud/speakers
```

---

## 12. Verification Checklist

Run these after initial setup to confirm everything is working:

```bash
# RDS PostgreSQL + pgvector
cd /app/lifelogger/backend
set -a
source .env
set +a
psql "$DATABASE_URL" -c "SELECT version(); SELECT * FROM pg_extension WHERE extname = 'vector';"

# Python + key packages
/app/venv/bin/python -c "
import fastapi, faster_whisper, speechbrain, sentence_transformers, pyannote.audio
print('All imports OK')
"

# FFmpeg
ffmpeg -version | head -1

# Directory permissions
ls -la /audio/
ls -la /app/

# API health
curl http://localhost:8000/health   # should return {"status": "ok"}

# Nginx proxy
curl http://your-vps-ip/health

# Cron job listed
crontab -u lifelogger -l
```
