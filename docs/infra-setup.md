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

## 2. PostgreSQL 16 Installation

```bash
# Add PostgreSQL apt repo
curl -fsSL https://www.postgresql.org/media/keys/ACCC4CF8.asc | \
    gpg --dearmor -o /usr/share/keyrings/postgresql.gpg

echo "deb [signed-by=/usr/share/keyrings/postgresql.gpg] \
    https://apt.postgresql.org/pub/repos/apt \
    $(lsb_release -cs)-pgdg main" > /etc/apt/sources.list.d/pgdg.list

apt update
apt install -y postgresql-16 postgresql-client-16 postgresql-server-dev-16

# Enable and start
systemctl enable postgresql
systemctl start postgresql

# Verify
psql --version   # should output: psql (PostgreSQL) 16.x
```

---

## 3. pgvector Extension

```bash
# Install pgvector from source (requires postgresql-server-dev-16)
cd /tmp
git clone --branch v0.7.0 https://github.com/pgvector/pgvector.git
cd pgvector
make
make install

# Enable in PostgreSQL
sudo -u postgres psql -c "CREATE EXTENSION IF NOT EXISTS vector;"
```

### Verify pgvector

```bash
sudo -u postgres psql -c "\dx"
# Should list: vector | 0.7.0 | public | vector data type and ivfflat and hnsw index access methods
```

---

## 4. Database Setup

```bash
# Switch to postgres user
sudo -u postgres psql

-- Inside psql:
CREATE USER lifelogger WITH PASSWORD 'changeme_strong_password';
CREATE DATABASE lifelogger OWNER lifelogger;
GRANT ALL PRIVILEGES ON DATABASE lifelogger TO lifelogger;
\c lifelogger
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
\q
```

### PostgreSQL Configuration Tuning

Edit `/etc/postgresql/16/main/postgresql.conf`:

```ini
# Memory (tune for 32GB VPS; leave headroom for ML models)
shared_buffers = 4GB
effective_cache_size = 12GB
maintenance_work_mem = 1GB
work_mem = 64MB

# WAL
wal_buffers = 64MB
checkpoint_completion_target = 0.9

# Connections
max_connections = 50

# Logging
log_min_duration_statement = 1000   # log queries > 1s
log_line_prefix = '%t [%p] [%d] '

# pgvector
# No special config needed; ivfflat uses work_mem for index build
```

Restart PostgreSQL after changes:
```bash
systemctl restart postgresql
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
        internal;   # only accessible via X-Accel-Redirect from FastAPI
    }
}
```

```bash
apt install -y nginx
ln -s /etc/nginx/sites-available/lifelogger /etc/nginx/sites-enabled/
rm /etc/nginx/sites-enabled/default
nginx -t && systemctl reload nginx
```

### Systemd Service for FastAPI

```ini
# /etc/systemd/system/lifelogger-api.service
[Unit]
Description=LifeLogger FastAPI
After=network.target postgresql.service

[Service]
User=lifelogger
Group=lifelogger
WorkingDirectory=/app/api
Environment="DATABASE_URL=postgresql+asyncpg://lifelogger:changeme_strong_password@localhost/lifelogger"
Environment="AUDIO_ROOT=/audio"
ExecStart=/app/venv/bin/uvicorn main:app --host 127.0.0.1 --port 8000 --workers 2
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

Store secrets in `/app/.env` (readable only by lifelogger):

```bash
touch /app/.env
chmod 600 /app/.env
chown lifelogger:lifelogger /app/.env
```

```env
# /app/.env
DATABASE_URL=postgresql+asyncpg://lifelogger:changeme_strong_password@localhost/lifelogger
AUDIO_ROOT=/audio
HF_TOKEN=hf_xxx
LOG_LEVEL=INFO
```

Load in Python with `python-dotenv` or read directly in systemd unit `EnvironmentFile=/app/.env`.

---

## 12. Verification Checklist

Run these after initial setup to confirm everything is working:

```bash
# PostgreSQL + pgvector
sudo -u postgres psql lifelogger -c "SELECT version(); SELECT * FROM pg_extension WHERE extname = 'vector';"

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
