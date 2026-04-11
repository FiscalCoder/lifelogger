# LifeLogger

A fully self-hosted passive life-recording system. Always-on voice activity detection on a low-power Android device captures speech throughout the day, uploads audio chunks to a personal VPS for nightly batch processing, and builds a queryable personal knowledge base — with persistent speaker identification across Tamil, English, and code-switched speech.

Zero recurring AI cost. Runs entirely on hardware you already own.

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────┐
│                  Qin F21 Pro (Android 11)            │
│                                                     │
│  Foreground Service (boot-persistent)               │
│  └─ WebRTC VAD polls mic @ 100ms, 16kHz mono        │
│     └─ Speech detected → record AAC chunk           │
│        └─ 2s silence → close chunk + timestamp      │
│           ├─ WiFi available → upload immediately    │
│           └─ No WiFi → SQLite queue → drain later   │
└───────────────────────┬─────────────────────────────┘
                        │ HTTPS POST /upload
                        │ (chunk + metadata)
                        ▼
┌─────────────────────────────────────────────────────┐
│             Hostinger KVM 8 VPS                     │
│                                                     │
│  FastAPI Ingestion API                              │
│  └─ /audio/pending/YYYY-MM-DD/ (received chunks)   │
│                                                     │
│  2AM Cron: Processing Pipeline                      │
│  ┌─────────────────────────────────────────────┐   │
│  │ Step 1: Diarization (pyannote-audio 3.x)    │   │
│  │         → speaker segments (SPEAKER_00...)   │   │
│  │ Step 2: Transcription (faster-whisper)       │   │
│  │         large-v3, int8, 8 cores parallel     │   │
│  │         auto lang detect (ta/en/mixed)       │   │
│  │ Step 3: Speaker ID (SpeechBrain ECAPA-TDNN) │   │
│  │         192-dim embeddings, pgvector         │   │
│  │         cosine similarity threshold: 0.82   │   │
│  │ Step 4: Assembly → structured JSON/day      │   │
│  │         → /audio/archive/                   │   │
│  └─────────────────────────────────────────────┘   │
│                                                     │
│  PostgreSQL + pgvector                             │
│  ├─ speaker_profiles (named, embeddings)           │
│  ├─ unknown_speakers (pending naming)              │
│  └─ transcript_segments (768-dim embeddings)       │
└──────────┬───────────────────────┬─────────────────┘
           │                       │
           ▼                       ▼
┌──────────────────┐   ┌──────────────────────────────┐
│ Speaker Naming   │   │ Query Interface              │
│ UI (plain HTML)  │   │                              │
│                  │   │ "What did I discuss with     │
│ List unknowns    │   │  Karthik last week?"         │
│ Play 10s sample  │   │                              │
│ Type name → save │   │ Semantic search via pgvector │
│ → reprocess      │   │ paraphrase-multilingual-     │
│                  │   │ mpnet-base-v2 (768-dim)      │
└──────────────────┘   └──────────────────────────────┘
```

---

## Hardware Constraints

| Resource | Spec | Notes |
|---|---|---|
| Phone | Qin F21 Pro, Android 11, 3GB RAM, 2120mAh | Compact form factor; realistic 8–10hr battery on 2120mAh |
| VPS | Hostinger KVM 8: 8 vCPU, 32GB RAM, 400GB NVMe | All ML inference runs here at 2AM batch |
| Cost | ₹0 incremental | VPS already owned; all models self-hosted |

---

## Tech Stack

### Mobile (Layer 1)

| Component | Library / Tool | Reason |
|---|---|---|
| Framework | React Native (bare workflow) | Cross-platform, JS bridge sufficient for non-RT use |
| VAD | react-native-webrtc-vad | WebRTC VAD, low CPU, 100ms polling |
| Audio recording | react-native-audio-recorder-player | AAC encoding, file-based output |
| Background service | react-native-background-actions | Android foreground service wrapper |
| Network detection | @react-native-community/netinfo | WiFi vs mobile detection for upload gating |
| Local queue | react-native-sqlite-storage | Persistent offline queue |
| Fallback | Kotlin Service class | If JS bridge degrades over 18hr sessions |

### Backend (Layers 2–6)

| Component | Library / Tool | Reason |
|---|---|---|
| API framework | FastAPI (Python) | Async, fast, minimal boilerplate |
| Transcription | faster-whisper large-v3 (int8) | Best multilingual quality, parallelizable, CPU-viable |
| Diarization | pyannote-audio 3.x | State-of-art speaker diarization, HuggingFace |
| Speaker ID | SpeechBrain ECAPA-TDNN | 192-dim embeddings, strong speaker discrimination |
| Vector store | PostgreSQL + pgvector | Unified DB; cosine similarity for speaker and semantic search |
| Text embeddings | paraphrase-multilingual-mpnet-base-v2 | 768-dim, handles Tamil + English + code-switching |
| Scheduler | cron (system) | Simple, reliable 2AM trigger |
| Naming UI | FastAPI + plain HTML | No framework overhead; one-time manual task |

---

## Build Order

| Week | Phase | Deliverable |
|---|---|---|
| 1 | F1 + B1 | RN app scaffolded + FastAPI ingestion API; chunks upload successfully |
| 2 | F2 + B2 | VAD + recording live; faster-whisper transcribes nightly |
| 3 | F3 + B3 | SQLite queue + WiFi drain; pyannote diarization integrated |
| 4 | F4 + B4 | Boot persistence + ECAPA speaker registry; enroll yourself |
| 5 | F5 | Speaker naming UI served from FastAPI; unknowns resolvable |
| 6+ | B5 + B6 + F6 | pgvector knowledge base, semantic embeddings, query interface |

---

## Directory Structure (VPS)

```
/audio/
├── pending/
│   └── YYYY-MM-DD/
│       ├── chunk_001.aac
│       └── ...
├── archive/
│   └── YYYY-MM-DD/
│       ├── chunk_001.aac
│       └── ...
└── samples/
    └── <unknown_id>.aac   ← 10s clip for naming UI

/app/
├── api/                   ← FastAPI app
├── pipeline/              ← cron-invoked processing scripts
├── models/                ← downloaded model weights
└── venv/                  ← Python virtual environment

/var/log/lifelogger/
├── pipeline.log
└── api.log
```

---

## Key Thresholds & Parameters

| Parameter | Value | Rationale |
|---|---|---|
| VAD poll interval | 100ms | WebRTC VAD minimum latency |
| Sample rate | 16kHz mono | Whisper native input; VAD requirement |
| Silence → chunk close | 2 seconds | Avoids mid-sentence splits; manageable chunk sizes |
| ECAPA similarity threshold | 0.82 cosine | Empirically strong for speaker discrimination; tunable |
| Transcription model | large-v3 int8 | Best accuracy; int8 fits 32GB RAM comfortably |
| Embedding model | paraphrase-multilingual-mpnet-base-v2 | 768-dim; supports Tamil + English |
| Cron time | 2AM | Off-peak; all uploads expected complete by then |
| Battery runtime | 8–10hr realistic | Plan mid-day charge for continuous coverage |
