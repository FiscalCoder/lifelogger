# Frontend Phases — LifeLogger

All mobile work targets **Android 11 on Qin F21 Pro** (3 GB RAM, 2120 mAh battery).
The app is a **pure Kotlin Android app** — no React Native, no JavaScript.

## Tech stack

| Layer | Choice | Rationale |
|-------|--------|-----------|
| Language | Kotlin | Native Android, no JS bridge overhead |
| UI | Views + Fragments + Navigation Component | Lightweight, no Compose overhead on low-RAM device |
| Database | Room (WAL mode) | Type-safe SQLite; concurrent reads while service writes |
| HTTP | OkHttp | Battle-tested, no reflection |
| Audio encoding | MediaCodec + MediaMuxer | AAC-LC at 64 kbps, compatible with Whisper pipeline |
| Background | Android Foreground Service | START_STICKY + BootReceiver for persistence |
| VAD | Energy-based (AudioRecord RMS) | No external library; sample-and-release keeps mic duty cycle ≈ 10% |

## Project structure

```
frontend/
├── build.gradle.kts           # root build (AGP 8.2.2, Kotlin 1.9.22)
├── settings.gradle.kts        # includes :app
├── gradle.properties          # AndroidX, WAL, Kotlin code style
├── local.properties           # server.base.url — NOT committed, set per device
└── app/
    └── src/main/
        ├── AndroidManifest.xml
        ├── kotlin/com/lifelogger/
        │   ├── MainActivity.kt          # bottom nav + permission request
        │   ├── LifeLoggerService.kt     # foreground service, orchestrator
        │   ├── VadEngine.kt             # IDLE-phase energy polling
        │   ├── AudioChunkManager.kt     # recording + silence detection + AAC encode
        │   ├── UploadQueue.kt           # WiFi-aware drain scheduler
        │   ├── BootReceiver.kt          # auto-start after reboot
        │   ├── config/AppConfig.kt      # all tunable constants + BuildConfig fields
        │   ├── db/
        │   │   ├── AppDatabase.kt       # Room singleton (WAL mode)
        │   │   ├── UploadQueueDao.kt    # CRUD + status queries
        │   │   └── UploadQueueEntity.kt # upload_queue table schema
        │   ├── ui/
        │   │   ├── StatusFragment.kt    # recording toggle, WiFi, queue counts
        │   │   ├── QueryFragment.kt     # natural-language search (scaffold)
        │   │   └── SpeakerNamingFragment.kt  # WebView wrapping /speakers/ui
        │   └── util/
        │       ├── NetworkUtil.kt       # WiFi detection via ConnectivityManager
        │       └── PermissionUtil.kt    # RECORD_AUDIO runtime permission
        └── res/
            ├── layout/                  # activity_main, fragment_*, item_query_result
            ├── navigation/nav_graph.xml # Status → Speakers → Search tabs
            ├── menu/bottom_nav_menu.xml
            ├── mipmap-anydpi-v26/       # adaptive launcher icon (mic on dark red)
            └── values/                  # strings, colors, themes
```

## Server IP configuration

The server runs on your computer; the phone connects via local WiFi.

1. Find your computer's local IP: `ipconfig` (Windows) or `ip addr` (Linux/Mac).
2. Edit `frontend/local.properties` (gitignored):
   ```
   server.base.url=http://192.168.1.XXX:8000
   ```
3. Rebuild. `AppConfig.SERVER_BASE_URL` reads this via `BuildConfig.SERVER_BASE_URL`.

---

## Phase F1 — App scaffolding + foreground service ✅

### What was built
- Pure Kotlin Android app (no React Native).
- `LifeLoggerService`: foreground service with `START_STICKY` + `PARTIAL_WAKE_LOCK` (12-hour timeout).
- `BootReceiver`: auto-starts the service after device reboot.
- `MainActivity` with bottom navigation (Status / Name Speakers / Search).
- `AppConfig`: all constants in one place; server URL injected via `BuildConfig`.

### Acceptance criteria
- [x] App installs on Android 11 (API 30)
- [x] Foreground notification visible after launch
- [x] Notification persists when app is swiped away (`stopWithTask="false"`)
- [x] Service restarts after crash (`START_STICKY`)
- [x] Service starts on reboot (BootReceiver)

---

## Phase F2 — VAD + audio recording ✅

### What was built
- `VadEngine`: IDLE-phase energy-based VAD using sample-and-release AudioRecord.
  - Each poll: open AudioRecord → read 10ms PCM frame → release → compute RMS.
  - Mic duty cycle ≈ 10% (aggressive) or 4% (power-saver). No continuous mic hold.
  - When speech detected: pauses polling, calls `onSpeechStart`.
  - `resume()` restarts polling after a chunk is finalised.
- `AudioChunkManager`: records speech + detects silence internally.
  - Opens AudioRecord (after VadEngine has released it — no conflict).
  - Records PCM to a temp file; computes RMS per frame for silence detection.
  - Silence ≥ threshold → finalizes: encodes PCM → AAC-LC (MediaCodec + MediaMuxer).
  - Calls `onChunkFinalized` which resumes VadEngine.

### VAD state machine
```
IDLE (VadEngine polling)
  ├─ RMS ≥ threshold → SPEECH
SPEECH (AudioChunkManager recording)
  ├─ RMS < threshold for ≥ silenceThresholdMs → IDLE (chunk finalised)
  └─ RMS ≥ threshold → (silence timer reset, stay SPEECH)
```

### Battery modes
| Mode | Poll interval | Silence threshold |
|------|--------------|-------------------|
| Aggressive (default) | 100 ms | 2 s |
| Power-saver | 250 ms | 5 s |

### Acceptance criteria
- [x] VAD starts/stops recording correctly on speech/silence
- [x] Chunk files are well-formed MPEG-4/AAC, decodable by ffprobe
- [x] AudioRecord conflict prevented (sample-and-release + handoff pattern)
- [x] Chunks shorter than 0.5 s discarded (false-positive noise)

---

## Phase F3 — Local SQLite queue + WiFi upload ✅

### What was built
- `AppDatabase` (Room, WAL mode): `upload_queue` table.
- `UploadQueueEntity`: `id, filePath, recordedAt, durationSeconds, attempts, status`.
- `UploadQueue`:
  - Scheduled drain every 60 s (initial delay = 0).
  - `drainIfWifi()`: checks WiFi via `NetworkUtil`, drains pending → retries failed.
  - Opportunistic upload immediately after each chunk is enqueued.
  - On 200: mark `uploaded`, delete local file.
  - On error: increment attempts; mark `failed` after 3 attempts.
  - `retryFailed()`: resets all failed chunks to pending.
- `StatusFragment`: shows pending / failed / uploaded counts, last upload time.
  - "Upload now" button triggers immediate drain.
  - "Retry failed" button appears when failed count > 0.

### Upload queue lifecycle
```
pending → (drain) → uploading → (200 OK) → uploaded + file deleted
                              → (error, attempts < 3) → pending
                              → (error, attempts ≥ 3) → failed
failed → (retryFailed) → pending
```

### Acceptance criteria
- [x] Disable WiFi → record → re-enable → all chunks drain automatically
- [x] SQLite queue survives app kill (Room persists to disk)
- [x] Failed chunks do not block new recordings
- [x] Notification reflects pending/failed counts

---

## Phase F4 — Boot persistence + battery optimization ✅

### What was built
- `BootReceiver`: starts `LifeLoggerService` on `BOOT_COMPLETED`.
- `PARTIAL_WAKE_LOCK` with 12-hour safety timeout (dead-man switch).
- Battery mode toggle in StatusFragment → relayed to both VadEngine and AudioChunkManager.
- `START_STICKY`: Android restarts the service after OOM kills.

### Acceptance criteria
- [x] Device reboots → service starts within 60 s
- [x] Wake lock is PARTIAL (CPU on, screen can off)
- [x] Battery mode switch adjusts both poll interval and silence threshold

---

## Phase F5 — Speaker naming UI ✅

### What was built
- `SpeakerNamingFragment`: WebView loading `AppConfig.SPEAKERS_UI_URL` (`/speakers/ui`).
- `WebBridge` JavaScript interface: backend page calls `window.WebBridge.onNamingComplete()` to pop back stack.
- Back-press handler: navigates WebView history before popping fragment.

### Acceptance criteria
- [x] Speaker naming HTML page loads in WebView
- [x] Back button navigates WebView history correctly
- [x] JS bridge allows backend page to signal completion

---

## Phase F6 — Query interface (scaffold) ⬜

### Status
Scaffold implemented — wired to `AppConfig.QUERY_ENDPOINT` (`/query`).
Backend B6 (Semantic Query API) must be deployed for results to appear.

### What was built
- `QueryFragment`: EditText + Search button + RecyclerView of results.
- On non-2xx response: shows "Backend not yet available" message.
- `QueryResultAdapter`: displays speaker, text, timestamp per result.

### Dependencies
- B6 (Semantic Query API) must be deployed.

---

## Building

```bash
cd frontend

# Set your local server IP in local.properties first:
# server.base.url=http://192.168.1.XXX:8000

# Debug APK
./gradlew assembleDebug

# Install directly to connected device
./gradlew installDebug
```

No `npm install` required — this is a pure Kotlin project.
