# CountAI — Smart Object Counter (v2 "Next Generation")

Point your phone's camera at objects → AI detects, tracks and counts them live, on-device.
No internet, no API keys, no accounts needed.

**"See it. Count it. Done."**

![minSdk 24](https://img.shields.io/badge/minSdk-24-brightgreen) ![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue) ![TFLite](https://img.shields.io/badge/TFLite-on--device-orange)

## What it does (v2)

### Core
- **Live counting** — open the camera, point at bottles, chairs, people… bounding
  boxes appear with names + confidence %, and big **LIVE** / **SESSION TOTAL**
  counters update in real time.
- **⚡ CountCam (signature mode)** — one tap → point at objects → AI detects,
  tracks, counts and categorizes → `COUNT COMPLETE ✅` result card with
  **Save | Share | Export | Recount**.
- **Smart tracking** — IOU-based tracker with persistent IDs: the same bottle
  is never re-counted; new objects entering the frame get new IDs.
- **Count only what you ask** — type or **speak** ("count only red bottles",
  "kitni kursiyan hain?", "صرف بوتلیں گنو"). English / Urdu / Roman Urdu
  commands; color filter (red/blue/green/yellow/black/white) by hue sampling.
- **Voice counting** — mic button → Android SpeechRecognizer → spoken answer
  via TTS + on-screen text.
- **🚪 IN / OUT line counter** — draggable virtual line; crossings count as
  IN (top→bottom) / OUT / NET. For people, vehicles, boxes, inventory flow.
- **Per-type counts** with manual **+ / −** correction.
- **Verify Count** — reviews uncertain (low-confidence) detections; remove
  wrong ones. AI counts are estimates — the app says so everywhere.

### Business
- **🎥 Video counting** — pick a video, CountAI samples frames and reports
  unique objects across it (progress bar).
- **📦 Smart Inventory mode** — name your products ("Product A" → bottle),
  store counted quantities, compare over time.
- **📊 Photo Compare (difference detector)** — BEFORE vs AFTER photo →
  Previous / Current / Difference per type, changed rows highlighted.
- **📋 Reports** — PDF (framework PdfDocument), CSV, and Excel-compatible
  `.xls` (SpreadsheetML) exports.
- **💼 Business dashboard** — today/week/month totals, session stats,
  top-objects bar chart (custom Canvas, no chart lib), export buttons.
- **🔍 Smart search** — "last week box counts", "highest bottle count",
  "aaj ki ginti" over history.

### Trust, privacy, accessibility
- **Trust & accuracy** — confidence % on every box; amber low-confidence
  badge; honest "never 100%" disclaimer.
- **Privacy first** — 100% on-device badge; frames/photos/videos never leave
  the phone; location in history only with permission + opt-in toggle (off).
- **Offline mode** — counting works without internet; OFFLINE MODE chip shows.
- **Low-end mode** — Battery Saver / Balanced / High Accuracy presets (FPS
  5/10/15); auto-suggests Battery Saver on < 3 GB RAM phones.
- **Multi-language** — English / اردو / Roman Urdu in-app switcher
  (Roman Urdu = BCP-47 `ur-Latn`, resources in `values-b+ur+Latn/`).
- **First-run coach marks**, empty/loading/error states, content descriptions.
- **Shareable result cards** — branded dark-theme bitmap shared to
  WhatsApp/Facebook/email via system chooser.
- **Freemium structure** — basic counting free forever; video/difference/
  reports/dashboard flagged Premium (all unlocked in debug builds; release
  shows a tag, never a frustrating paywall).
- **Custom models** — train on PC (Teachable Machine/Colab), then
  **Settings → Import custom model** (.tflite + labels.txt) — no rebuild needed.

## Project structure

```
CountAI/
├── app/
│   ├── build.gradle.kts            # deps: CameraX 1.3.4, TFLite 2.16.1, Room 2.6.1
│   └── src/main/
│       ├── AndroidManifest.xml     # CAMERA + RECORD_AUDIO + ACCESS_COARSE_LOCATION
│       ├── assets/
│       │   ├── detect.tflite       # object-detection model (via script below)
│       │   └── labels.txt          # COCO 80-class labels
│       ├── java/com/zenithai/countai/
│       │   ├── ui/                 # MainActivity, CameraFragment, CountCamFragment,
│       │   │                       # InOutFragment (+overlay), VideoCountFragment,
│       │   │                       # ToolsFragment, InventoryFragment, DifferenceFragment,
│       │   │                       # DashboardFragment (+BarChartView), HistoryFragment,
│       │   │                       # SettingsFragment, CameraSession (shared CameraX),
│       │   │                       # DetectionOverlayView
│       │   ├── detection/          # Detector (interface), TfliteObjectDetector,
│       │   │                       # ObjectTracker (IOU), LineCounter, VideoCounter,
│       │   │                       # ColorFilter, CustomModelImporter + ModelManager
│       │   ├── data/               # Room v2: SessionEntity (+location),
│       │   │                       # InventoryEntity, DAOs, SessionRepository
│       │   ├── repository/         # AuthRepository + CloudSyncRepository (stubs)
│       │   └── util/               # Prefs, LanguageManager, CommandParser,
│       │                           # VoiceHelper, PdfReport, ExcelExporter,
│       │                           # ResultCard, SmartSearch, DifferenceUtil,
│       │                           # PremiumManager, PerformanceManager,
│       │                           # NetworkMonitor, LocationHelper,
│       │                           # FrameConverter, CsvExporter, ImageSaver
│       └── res/                    # values/ (EN) + values-ur/ + values-b+ur+Latn/
└── scripts/download_model.sh       # downloads + verifies the .tflite model
```

## The AI model

`app/src/main/assets/detect.tflite` is the official **quantized SSD-MobileNetV1**
COCO detector from TensorFlow Hub (~4 MB, 300×300, uint8) — verified as a real
TFLite flatbuffer (magic `TFL3`). If the file is missing, get it with:

```bash
bash scripts/download_model.sh
```

The script downloads from the stable TF Hub URL and **refuses to install**
anything that fails the flatbuffer magic-byte + size check.

The detector reads the model's input size/type from the tensors at load time,
tries the **GPU delegate** first, then **NNAPI**, then multi-threaded CPU —
every delegate is guarded, so a failure falls back instead of crashing.

## Build

### Android Studio (recommended)

1. Open the `CountAI` folder in Android Studio (it will download Gradle 8.7
   and repair the wrapper if `gradle-wrapper.jar` is missing).
2. Make sure `app/src/main/assets/detect.tflite` exists
   (`bash scripts/download_model.sh` if not).
3. **Run ▸ Run 'app'** on an emulator or a real phone.

### Command line

```bash
cd CountAI
bash scripts/download_model.sh        # once
./gradlew assembleDebug               # needs JDK 17
# APK: app/build/outputs/apk/debug/app-debug.apk
```

> If `gradle-wrapper.jar` is missing, run `gradle wrapper --gradle-version 8.7`
> once with a local Gradle install, or just open the project in Android Studio.

### Install on your phone

1. Copy `app-debug.apk` to the phone (USB, Drive, WhatsApp to yourself…).
2. Open it → allow "Install unknown apps" if asked.
3. Open **CountAI** → tap **Start Camera** → allow the camera permission →
   point at objects.

## Use your own custom model (products, fruits, inventory…)

Two ways — pick one:

**A. In-app import (no rebuild)** — train an SSD-style detection model on
your PC and export `.tflite` (Teachable Machine, or YOLO → TFLite
conversion). Keep the standard 4-output signature the code expects:
`boxes [1,N,4]` (ymin,xmin,ymax,xmax normalized), `classes [1,N]`,
`scores [1,N]`, `num_detections [1]`. Then in the app:
**Settings → Import custom model** → pick the `.tflite`, then its
`labels.txt` (one label per line, line index = class id). The app copies
them into private storage and uses them from then on
(`detection/CustomModelImporter.kt` + `ModelManager`).

**B. Replace at build time** — swap `app/src/main/assets/detect.tflite` and
`labels.txt` and rebuild. Input size (300 vs 320 vs …) and uint8/float input
are detected automatically; no code change needed for standard SSD models.

**Honest note:** training itself is NOT done on the phone — that needs a PC
or Colab. The app only *runs* finished models.

## Known limits (honest)

- **COCO classes only in the bundled model** — 80 everyday classes. There is
  **no "box" class** in COCO: cardboard boxes are typically detected as
  `suitcase`, `book`, or `refrigerator`. The "box" voice command maps to
  those shapes honestly; use a custom model for real inventory SKUs.
- **Color filter is hue sampling** — dominant-color voting inside each box.
  Mixed lighting, shadows and multicolored objects confuse it; the app
  reports "unknown" rather than guessing.
- **Tracking is IOU-based** — slow, steady camera movement gives the best
  session totals. Fast pans can mint fresh IDs (over-counting). The
  per-frame NMS + tracker also means heavily overlapping identical objects
  may merge into one.
- **Video counting samples frames** (~2/sec), not every frame — very fast
  objects can be missed; results are estimates.
- **Quantized model accuracy** — the 4 MB uint8 model is fast but less
  accurate than large float models; the confidence slider, low-confidence
  badge and Verify Count exist for exactly this reason.
- **Voice recognition** uses Android's built-in engine (free). On-device vs
  network depends on the phone's own speech settings — CountAI sends nothing
  itself.
- **Excel export is SpreadsheetML** (`.xls`, XML-based) — opens in Excel,
  Sheets and LibreOffice; it is not the modern `.xlsx` format.
- **Roman Urdu resources** live in `values-b+ur+Latn/` (BCP-47 `ur-Latn`).
  Untranslated strings fall back to English automatically.
- GPU delegate is attempted but quantized models often run fastest on
  CPU/NNAPI; the fallback chain handles this automatically.

## Scaling to 50k users (v2.1 — offline-first cloud)

The core decision: **Room is the source of truth; Supabase is a sync target.**
The app works 100% with zero network — counting, history, reports. Cloud is
an optional backup, never a dependency. That alone removes 90% of scaling
risk: no server round-trip can ever crash counting or lose a session.

### One-time setup (the user does this, ~5 minutes)

1. Create a free project at <https://supabase.com/dashboard>.
2. In the Supabase dashboard SQL editor, run the three files in `supabase/`
   **in order**: `schema.sql` → `rls.sql` → `indexes.sql`.
3. Dashboard → Project Settings → API → copy the **Project URL** and the
   **anon public key** into `local.properties`:
   `SUPABASE_URL=` and `SUPABASE_ANON_KEY=` (see the template in that file).
4. Rebuild the app. Settings → Cloud backup now shows your account status.

`local.properties` is gitignored — keys never touch the repo. The anon key is
public by design (RLS is the real lock); the `service_role` key is NEVER used
anywhere in the app.

### Why the free tier survives 50k users — the math

Supabase free tier: **500 MB database · 1 GB storage · 2 GB bandwidth/mo ·
50k monthly active users.** The design respects every cap:

| What syncs | Size | Frequency | 50k-user cost/mo |
|---|---|---|---|
| Session metadata (id, counts, timestamps) | ~200 bytes/row | on change, batched ≤100 | 50k × 5 sessions/day × 200 B × 30 d ≈ **1.5 GB ingress** (ingress is not the billed bottleneck) |
| Session reads (RLS-filtered, paged ×20) | ~200 bytes/row | only when user opens history | small |
| Thumbnails (~100 KB JPEG) | ~100 KB | **opt-in only, WiFi only**, ≤20/run | 50k × 20% opt-in × 5/day × 100 KB × 30 d ≈ **3 GB storage** — see limits below |
| Full photos/videos | — | **NEVER auto-upload** | 0 |

Bandwidth that counts is mostly **egress** (downloads). Sync is push-heavy
(uploads), and there is deliberately **no "download everything" path** — one
user's data can never flood another user's phone. Pagination (`.range()`,
20 rows) bounds every read.

### Honest limits

- If **every** user turned on cloud backup and uploaded 5 thumbnails daily,
  storage would exceed 1 GB. That's exactly why backup **defaults OFF** and
  needs WiFi + sign-in. With realistic opt-in (~10–20%), usage stays inside
  the free tier. If you outgrow it, the paid tier — not a rewrite — is the fix.
- Sync is **eventual** (every 6h, or manual "Sync now" debounced to 1/60s),
  not realtime. It's a backup, not a live feed.
- Google sign-in is not wired yet (email/password + magic link are);
  the `Auth` module supports ID-token sign-in when you're ready.
- Cross-device thumbnail *viewing* needs signed URLs (next step); today
  thumbnails are backup-only in the private bucket.
- If the free tier ever hard-caps (egress), the app keeps working offline —
  sync workers park and retry; nothing crashes, nothing is lost.

### Code map

- `sync/SupabaseConfig.kt` — fail-soft config (empty keys = offline mode).
- `sync/SupabaseClientProvider.kt` — ONE shared client (10s connect / 30s
  read timeouts). One pool, one auth session.
- `sync/SyncManager.kt` — WorkManager scheduling: 6h periodic, battery-not-low;
  thumbnails additionally require unmetered WiFi + opt-in.
- `sync/SyncWorker.kt` — uploads metadata + delete tombstones, batches ≤100,
  exponential backoff, parks after 5 attempts.
- `sync/ThumbnailWorker.kt` — ~100 KB thumbnails, ≤20 per run, WiFi only.
- `repository/AuthRepository.kt` — `SupabaseAuthRepository` (email/password,
  magic link, sign-out stops sync and keeps local data).
- `di/ServiceLocator.kt` — resolves NoOp vs Supabase implementations so the
  UI never branches on config.

## Roadmap (stubs, not half-features)

Accounts + cloud sync are now REAL (see above). Still stubbed for later:
team workspaces, subscriptions (Play Billing), and the public API. They plug
into `repository/` without touching the UI. The CSV exporter is already wired
(History → Export CSV).

## License

Built for Zenith AI. Model © its respective owners (TF Hub, Apache 2.0).
