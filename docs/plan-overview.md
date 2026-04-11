# Plan Overview — LifeLogger

This document describes the high-level phasing strategy, dependencies between frontend and backend work, and the sequence in which phases should be executed.

---

## Phase Summary

| Phase ID | Layer | Name | Delivers |
|---|---|---|---|
| F1 | Frontend | RN App Scaffolding + Foreground Service | App boots, service runs, basic HTTP upload works |
| B1 | Backend | FastAPI Ingestion API | Receives chunks, writes to disk, API is testable |
| F2 | Frontend | VAD + Audio Recording | Real VAD-gated speech capture, chunked AAC files |
| B2 | Backend | faster-whisper Transcription | Nightly transcription of all pending chunks |
| F3 | Frontend | SQLite Queue + WiFi Upload | Offline-tolerant; drains on WiFi; no data loss |
| B3 | Backend | pyannote Diarization | Speaker-segmented transcripts, anonymous labels |
| F4 | Frontend | Boot Persistence + Battery Optimization | Survives reboot; wakelock tuned |
| B4 | Backend | ECAPA Speaker Registry | Named speakers persist; embeddings matched nightly |
| F5 | Frontend | Speaker Naming UI | Operator names unknowns via browser |
| B5 | Backend | pgvector Knowledge Base | Segments embedded and stored; searchable |
| B6 | Backend | Semantic Query API | Natural language queries over knowledge base |
| F6 | Frontend | Query Interface | User-facing search UI (future) |

---

## Dependency Graph

```
F1 ──────────────────────────────────────────────────────► F2
F1 ─── (upload endpoint needed) ───────────────────────── B1
B1 ──────────────────────────────────────────────────────► B2
F2 ─── (real audio needed for testing) ─────────────────► F3
B2 ──────────────────────────────────────────────────────► B3
F3 ─── (offline persistence complete) ──────────────────► F4
B3 ──────────────────────────────────────────────────────► B4
B4 ──────────────────────────────────────────────────────► F5
B4 ──────────────────────────────────────────────────────► B5
B5 ──────────────────────────────────────────────────────► B6
B6 ──────────────────────────────────────────────────────► F6
```

**Critical path:** F1 → B1 → B2 → B3 → B4 → B5 → B6 → F6

F1 and B1 can begin simultaneously since neither depends on the other — F1 uses a mock endpoint initially.

---

## Phase Groupings by Week

### Week 1 — Foundation

- **F1**: Scaffold RN app; foreground service; mock upload to localhost/ngrok
- **B1**: FastAPI receives chunks; stores to `/audio/pending/YYYY-MM-DD/`; return 200

Both can proceed independently. At end of week, connect real app to real API.

**Gate:** App uploads a real AAC chunk to the VPS and it appears on disk.

---

### Week 2 — Transcription Loop

- **F2**: Integrate WebRTC VAD; trigger real recordings; chunk on 2s silence
- **B2**: faster-whisper pipeline; cron at 2AM; outputs per-day JSON

F2 depends on F1 complete. B2 depends on B1 having real audio to process.

**Gate:** End of day, chunks from phone appear transcribed in a JSON file on VPS.

---

### Week 3 — Offline Resilience + Diarization

- **F3**: SQLite offline queue; WiFi detection; drain logic
- **B3**: Add pyannote diarization before whisper in pipeline; segment JSON updated

F3 depends on F2. B3 depends on B2 running.

**Gate:** Simulate WiFi off/on cycle — no audio lost. Transcripts include SPEAKER_00/01 labels.

---

### Week 4 — Speaker Identity

- **F4**: Boot persistence via RECEIVE_BOOT_COMPLETED; wakelock review; battery profiling
- **B4**: SpeechBrain ECAPA; pgvector similarity; enroll yourself; threshold at 0.82

F4 depends on F3. B4 depends on B3 providing speaker segments with audio clips.

**Gate:** Reboot phone — service auto-restarts. Your own voice is correctly identified in nightly run.

---

### Week 5 — Speaker Naming UI

- **F5**: FastAPI serves plain HTML naming UI; play samples; name → save → reprocess
- (No new mobile work this week unless F4 polish needed)

F5 depends on B4 producing unknown speaker records in DB.

**Gate:** Operator opens browser, names all unknowns from a sample day, speaker appears correctly resolved in next run.

---

### Week 6+ — Knowledge Base and Query

- **B5**: Transcript segments embedded with paraphrase-multilingual-mpnet-base-v2; stored in pgvector
- **B6**: Semantic query API endpoint; returns ranked segments with metadata
- **F6**: Simple query UI (future, low priority)

B5 depends on B4 (segments need speaker names). B6 depends on B5. F6 depends on B6.

**Gate:** Query "What did I discuss with [name] last week?" returns relevant transcript segments.

---

## Frontend vs Backend Separation

### Frontend Scope

The frontend includes everything that runs on the Qin F21 Pro **plus** the speaker naming UI (which runs in a browser but is served from the VPS). The naming UI is classified as frontend because it is a user-facing interaction layer, not a processing component.

- Phases F1–F4: Android app
- Phase F5: Browser-based HTML UI (served by FastAPI)
- Phase F6: Future query UI

### Backend Scope

Everything that runs on the VPS — ingestion, processing, storage, and APIs.

- Phase B1: API + file ingestion
- Phase B2–B4: Processing pipeline (transcription, diarization, speaker ID)
- Phase B5–B6: Knowledge base and query

---

## Risk Register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| JS bridge degrades in 18hr foreground service | Medium | High | Kotlin Service fallback defined in F1 |
| Battery dies mid-day | High | Medium | SQLite queue survives; plan mid-day charge |
| pyannote diarization slow on 8 vCPU | Low | Medium | int8 quantization; parallelization across cores |
| ECAPA false match (< 0.82) | Medium | Medium | Threshold tunable; log near-miss scores |
| Tamil VAD accuracy | Medium | Medium | WebRTC VAD is language-agnostic (energy-based) |
| WiFi unavailability at upload time | High | Low | SQLite queue handles this by design |
| pgvector not installed on VPS | Low | High | infra-setup.md covers this explicitly |

---

## Non-Goals (Explicitly Out of Scope)

- Real-time transcription or streaming to VPS
- Cloud AI APIs (OpenAI, AssemblyAI, etc.)
- Multi-device support
- User accounts or authentication beyond local access
- iOS support
- Video recording
