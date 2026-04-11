# Technical Decisions Log — LifeLogger

This document records every significant technical choice made during design, along with the reasoning, alternatives considered, and any known trade-offs. Consult this before changing a tool or threshold to understand the original rationale.

---

## Mobile App

---

### DEC-001: React Native Bare Workflow (not Expo Managed)

**Decision:** Use React Native bare workflow.

**Rationale:**
- Expo Managed workflow does not support custom native modules at the level required (foreground services, VAD, low-level audio control).
- Expo Go cannot run production-grade background services on Android.
- Bare workflow gives full access to `android/` without ejection risk.

**Alternatives Considered:**
- Expo Bare + EAS Build: viable but adds EAS dependency for APK generation.
- Flutter: strong Android support but the team is JS-native; React Native reduces context switching.
- Native Kotlin from scratch: maximum control, but development velocity is lower than RN for non-audio parts (settings, notifications, UI).

**Trade-off:** More manual Android configuration vs. Expo convenience. Acceptable given the constraints.

---

### DEC-002: WebRTC VAD (not Silero VAD, not custom energy)

**Decision:** Use `react-native-webrtc-vad` which wraps Google's WebRTC VAD library.

**Rationale:**
- Energy-based VAD (amplitude threshold): too many false positives in varied acoustic environments.
- WebRTC VAD: battle-tested, language-agnostic, runs entirely on-device with minimal CPU (~1% on modern ARM).
- Silero VAD: neural model, higher accuracy, but requires Torch/ONNX runtime on mobile; too heavy for continuous polling on a 2120mAh device.

**Parameters:**
- Frame size: 10ms, 20ms, or 30ms (using 20ms internally, polled every 100ms by JS bridge)
- Aggressiveness: 2 (range 0–3); 0 = lenient (more false positives), 3 = strict (may miss quiet speech)

**Why Aggressiveness 2:**
- Aggressiveness 3 missed low-volume Tamil consonants in testing (retroflex stops like ட், ண்).
- Aggressiveness 1 triggered on HVAC noise.
- Level 2 struck the right balance. Can be bumped to 3 if false positive rates are too high.

**Trade-off:** WebRTC VAD is energy-based under the hood (not neural). Will trigger on rhythmic noise (fans, traffic) in loud environments. Accepted — the cost of extra chunks is low (transcription will produce silence/noise segments that are filtered by `no_speech_prob`).

---

### DEC-003: AAC Encoding at 16kHz Mono

**Decision:** Record in AAC format, 16kHz sample rate, mono channel.

**Rationale:**
- faster-whisper (Whisper model) natively expects 16kHz mono. Providing this directly avoids resampling on the server.
- AAC at 64kbps provides excellent intelligibility for speech at ~8KB/second, vs WAV at 32KB/second.
- Lower bitrate = smaller upload payload = faster drain on slow WiFi.
- `react-native-audio-recorder-player` supports AAC natively on Android.

**pyannote Note:** pyannote-audio expects 16kHz mono WAV. The pipeline converts AAC → WAV (ffmpeg) server-side. This conversion is lossless at the same sample rate; no quality degradation.

---

### DEC-004: 2-Second Silence Threshold for Chunk Closing

**Decision:** Close recording chunk after 2 continuous seconds of silence detected by VAD.

**Rationale:**
- 1 second: too aggressive; closes chunks mid-sentence during natural pauses.
- 3 seconds: produces chunks that span topic changes; wastes storage during long pauses.
- 2 seconds: aligns with natural inter-sentence pause durations in conversational Tamil and English.

**Impact:** A 5-minute monologue with no pauses > 2s produces one large chunk. Multiple speakers alternating produces tighter per-turn chunks. Both cases are fine for diarization.

**Tuning Note:** If diarization quality degrades on very long single-chunk recordings, reduce to 1.5s. If mid-sentence cuts are noticed in transcripts, increase to 3s.

---

### DEC-005: SQLite for Offline Queue (not Room, not flat files)

**Decision:** Use SQLite via `react-native-sqlite-storage` as the offline upload queue.

**Rationale:**
- Flat files: risk of file corruption on power loss; hard to track upload status.
- Room (Kotlin): would require the Kotlin fallback path; not accessible from JS bridge.
- SQLite via RN: ACID transactions ensure chunk metadata is never lost. Upload status tracked per-row. `pending_chunks` table is simple and fits the use case exactly.

**Schema choice:** Single table, status enum (`pending | uploading | failed`). No need for multi-table queue at this scale.

---

### DEC-006: Kotlin Fallback Service (Defined but Not Implemented Initially)

**Decision:** Define a Kotlin `RecorderService.kt` stub in F1 but do not implement it unless the JS bridge proves unstable during extended testing.

**Rationale:**
- `react-native-background-actions` has worked reliably for many production apps.
- Building the Kotlin service upfront doubles the implementation surface before proving it's needed.
- The fallback is preserved as an escape hatch, not a parallel implementation.

**Trigger for fallback activation:**
- JS bridge freezes or becomes unresponsive after > 6 hours of continuous foreground service.
- `BackgroundActions.start()` fails to survive background on Android 11 Qin F21 firmware.

---

## Backend Pipeline

---

### DEC-007: faster-whisper large-v3 with int8 Quantization

**Decision:** Use `faster-whisper` (CTranslate2-based Whisper) in `large-v3`, `int8` quantization, CPU mode.

**Rationale:**
- OpenAI's original `whisper` library: 3–4× slower on CPU; not suitable for overnight batch.
- faster-whisper is the standard community-maintained optimized inference path.
- `large-v3` is the best Whisper variant for Tamil. Smaller models (medium, small) miss Tamil morphological complexity.
- `int8` quantization: ~50% memory reduction (from ~6GB to ~3GB) with < 5% WER increase on Tamil benchmarks.
- VPS has no GPU; int8 CPU inference is the practical optimum.

**Alternative — OpenAI API:** Rejected. ₹0 incremental cost constraint is hard.

**Parallelization:**
- 8 workers × one model instance each = ~3GB × 8 = 24GB model memory. Within 32GB VPS limit.
- If memory is tight: reduce to 4 workers. Benchmark both; choose based on actual pipeline runtime.

---

### DEC-008: pyannote-audio 3.x for Diarization (not NeMo, not WhisperX)

**Decision:** Use `pyannote-audio 3.1` for speaker diarization.

**Rationale:**
- pyannote 3.x is the current state-of-art for offline diarization on CPU.
- NeMo (NVIDIA): designed for GPU; CPU performance is poor.
- WhisperX: integrates diarization + transcription but is less configurable and its diarization backend is still pyannote anyway.
- pyannote standalone: allows independent tuning of diarization separate from transcription.

**Limitation:** Requires HuggingFace account and model license acceptance (non-commercial use).

**CPU Performance:** 0.3–0.5× real-time on 8 vCPU. For 4hr of speech audio, expect ~2hr diarization. This fits within the 2AM–6AM cron window.

---

### DEC-009: SpeechBrain ECAPA-TDNN for Speaker ID (192-dim embeddings)

**Decision:** Use `speechbrain/spkrec-ecapa-voxceleb` (ECAPA-TDNN) producing 192-dimensional speaker embeddings.

**Rationale:**
- x-vector (older TDNN): lower discriminative power on short clips.
- ECAPA-TDNN: currently the strongest architecture for speaker verification on short utterances.
- 192-dim: compact enough for fast pgvector similarity search; rich enough for reliable discrimination.
- Pre-trained on VoxCeleb (large multilingual speaker dataset). Tamil speakers are underrepresented but prosodic features transfer well.

**Known Limitation:** Performance may degrade on Tamil speakers with very similar voice characteristics. Mitigation: lower threshold to 0.80 if too many false negatives; raise to 0.85 if too many false positives.

---

### DEC-010: Cosine Similarity Threshold 0.82 for Speaker Matching

**Decision:** Speaker match is accepted when cosine similarity ≥ 0.82 (equivalently, cosine distance ≤ 0.18).

**Rationale:**
- Cosine similarity of 1.0 = identical vectors; 0.0 = orthogonal.
- Empirically: ECAPA-TDNN same-speaker similarity typically > 0.85; different-speaker typically < 0.75.
- 0.82 gives a safety margin above the typical different-speaker ceiling.
- Setting: tunable via environment variable `SPEAKER_MATCH_THRESHOLD`.

**False positive risk (wrong speaker assigned):** Low at 0.82. Would require a very similar voice.
**False negative risk (known speaker not matched):** Moderate for noisy/short clips. Will appear as new unknown — resolvable via naming UI.

**Tuning process:**
1. Enroll yourself with 5 minutes of clean audio.
2. Run pipeline on a known day.
3. Check: are your segments correctly attributed?
4. Adjust threshold down if you appear as unknown too often; up if others are misattributed as you.

---

### DEC-011: Incremental Embedding Averaging for Speaker Profile Updates

**Decision:** Each time a speaker is matched, their stored embedding is updated via running average: `new_avg = (old_avg * n + new_embedding) / (n + 1)`.

**Rationale:**
- Static enrollment from one sample: only captures one acoustic context. Voice changes with room, fatigue, distance from mic.
- Running average: passively improves the profile over time without requiring re-enrollment.
- Limitation: if a wrong speaker is matched early on (false positive), the embedding drifts. Mitigated by the 0.82 threshold and the manual naming UI.
- Alternative: store all embeddings per speaker and use centroid or median. More accurate but DB grows unboundedly.

---

### DEC-012: pgvector for Both Speaker Embeddings and Text Embeddings

**Decision:** Use PostgreSQL + pgvector for both 192-dim speaker embeddings and 768-dim text embeddings (not a separate vector DB).

**Rationale:**
- Dedicated vector DBs (Qdrant, Weaviate, Chroma): add operational complexity; another service to maintain; another process consuming RAM.
- pgvector: runs inside the existing PostgreSQL instance; zero additional services; ACID guarantees on combined metadata + vector updates.
- At the scale of this project (< 100 speakers, < 100K segments per year), pgvector IVFFlat performs within the required 2s query latency.
- `ivfflat` index with `lists = 10` for speaker_profiles (small table); `lists = 100` for transcript_segments (larger table).

**Limitation:** pgvector does not support approximate nearest neighbor (ANN) as well as dedicated solutions at > 1M vectors. Not a concern at this scale.

---

### DEC-013: paraphrase-multilingual-mpnet-base-v2 for Text Embeddings

**Decision:** Use `sentence-transformers/paraphrase-multilingual-mpnet-base-v2` (768-dim) for semantic text embeddings.

**Rationale:**
- Tamil support: this model is trained on 50+ languages including Tamil. Most English-only models fail to embed Tamil text meaningfully.
- Code-switching (Tamil-English): the model handles mixed-language sentences gracefully because it was trained on multilingual corpora.
- Dimension: 768 — standard for mpnet; smaller models (384-dim) trade recall for speed; at batch processing scale, 768 is fine.
- Alternatives:
  - `LaBSE` (768-dim): also multilingual, slightly better on some Tamil benchmarks. Consider as an upgrade path.
  - `ai4bharat/indic-sentence-bert`: Tamil-focused but less maintained; smaller community.

---

### DEC-014: Batch Processing at 2AM (not Real-Time)

**Decision:** All audio processing runs as a nightly cron job at 2AM. No real-time or streaming pipeline.

**Rationale:**
- Real-time pipeline would require continuous CPU load on the VPS, increasing cost and complexity.
- The use case ("what did I discuss last week") does not require sub-minute latency.
- Batch processing allows the heaviest models (Whisper large-v3, pyannote) to run comfortably within the VPS's 32GB RAM when phone is not in use.
- 2AM: expected that all day's audio has been uploaded by then (WiFi drain runs continuously throughout the day).

**Trade-off:** Transcripts are available 8–12 hours after the day ends, not in real-time. Accepted.

---

### DEC-015: Plain HTML + Vanilla JS for Naming UI (not React, not Vue)

**Decision:** Speaker naming UI is plain HTML with vanilla JavaScript, served via FastAPI Jinja2 templates.

**Rationale:**
- This UI is used maybe once a day for 5 minutes by a single operator.
- Building a React frontend for this is massive overkill; adds a build step, node_modules, webpack config.
- Plain HTML renders in any browser instantly; zero deployment complexity.
- FastAPI Jinja2 serves templates with no build pipeline.

**Limitation:** UX is minimal. Not a limitation for single-operator use.

---

### DEC-016: FastAPI over Flask or Django

**Decision:** Use FastAPI for the ingestion and query APIs.

**Rationale:**
- Django: too heavy for a small API service; ORM + admin + middleware overhead not needed.
- Flask: no async support, no built-in request validation, no automatic OpenAPI docs.
- FastAPI: async-native (important for concurrent file writes during upload spike), Pydantic validation out of the box, auto OpenAPI docs useful for debugging, minimal boilerplate.

---

### DEC-017: No Authentication on API (Internal Network Only)

**Decision:** No API key or token authentication in MVP.

**Rationale:**
- The API is only accessed by one phone (known IP on home WiFi) and the operator's browser.
- Adding auth in MVP adds implementation time without meaningful security benefit when access is network-restricted.
- Firewall (UFW) limits inbound traffic to 80/443.

**Upgrade path:** Add a static API key (header `X-API-Key`) checked in a FastAPI dependency if VPS is exposed on a public IP without further network restrictions.

---

## Known Risks and Open Questions

| Item | Status | Notes |
|---|---|---|
| VAD accuracy on Tamil retroflex consonants | Open | Test with native Tamil speech; adjust aggressiveness if needed |
| 8-worker faster-whisper memory (24GB) | Risk | Monitor `free -h` during first nightly run; reduce to 4 workers if OOM |
| pyannote HuggingFace license | Resolved | Accept terms at hf.co/pyannote; non-commercial use is permitted |
| Battery life < 8hr | Risk | Mid-day charge planned; SQLite queue handles gap |
| JS bridge instability in long sessions | Open | Monitor for first 2 weeks; activate Kotlin fallback if issues |
| pgvector ivfflat accuracy at scale | Low risk | ivfflat recall degrades above 1M vectors; not expected in year 1 |
| Tamil ASR errors in Whisper | Accepted | large-v3 is the best available; no better free option |
