# CountAI — Smart Object Counter

Point your phone's camera at objects → AI detects and counts them live, on-device.
No internet, no API keys, no accounts needed.

![minSdk 24](https://img.shields.io/badge/minSdk-24-brightgreen) ![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue) ![TFLite](https://img.shields.io/badge/TFLite-on--device-orange)

## What it does

- **Live counting** — open the camera, point at bottles, chairs, people… bounding
  boxes appear with names, and big **LIVE** / **SESSION TOTAL** counters update in
  real time.
- **Smart tracking** — an IOU-based object tracker gives every physical object a
  persistent ID, so the same bottle isn't re-counted while the camera looks at it.
  New objects entering the frame get new IDs and are counted.
- **Per-type counts** — `Bottle — 12`, `Chair — 4`, `Person — 3`, each with
  manual **+ / −** correction.
- **Count-only filter** — chips to count just one object type, or everything.
- **Photo mode** — pick a photo from the gallery and count objects in it.
- **History** — every saved result (date/time, per-type counts, photo) in a
  Room database; tap for details, share, delete, or export all to **CSV**.
- **Confidence control** — adjustable threshold (Settings), plus a "low
  confidence — improve lighting" warning badge.
- **Dark camera UI**, large typography, minimal buttons; detection throttled
  (5–15 FPS, adjustable) so low-end phones stay smooth.
- **Private by design** — the model runs fully on the phone. Frames never leave
  the device.

## Project structure

```
CountAI/
├── app/
│   ├── build.gradle.kts            # deps: CameraX 1.3.4, TFLite 2.16.1, Room 2.6.1
│   └── src/main/
│       ├── AndroidManifest.xml     # CAMERA permission + FileProvider
│       ├── assets/
│       │   ├── detect.tflite       # object-detection model (via script below)
│       │   └── labels.txt          # COCO 80-class labels
│       ├── java/com/zenithai/countai/
│       │   ├── ui/                 # MainActivity, CameraFragment, HistoryFragment,
│       │   │                       # SettingsFragment, DetectionOverlayView, HistoryAdapter
│       │   ├── detection/          # Detector (interface), TfliteObjectDetector,
│       │   │                       # ObjectTracker (IOU)
│       │   ├── data/               # Room: SessionEntity, HistoryDao, CountAiDatabase,
│       │   │                       # SessionRepository
│       │   ├── repository/         # AuthRepository + CloudSyncRepository
│       │   │                       # (clean STUBS for the roadmap — not half-built)
│       │   └── util/               # Prefs, PermissionHelper, ImageSaver, CsvExporter
│       └── res/                    # XML layouts (ViewBinding) + dark theme
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

The UI only talks to the `Detector` interface
(`detection/Detector.kt`), so swapping models is a contained change:

1. Train an SSD-style detection model and export `.tflite`
   (Teachable Machine, or YOLO → TFLite conversion).
   Keep the standard 4-output signature the code expects:
   `boxes [1,N,4]` (ymin,xmin,ymax,xmax normalized), `classes [1,N]`,
   `scores [1,N]`, `num_detections [1]`.
2. Replace `app/src/main/assets/detect.tflite`.
3. Replace `app/src/main/assets/labels.txt` — **one label per line, line
   index = class id** (use `???` for unused ids; they are skipped).
4. Rebuild. Input size (300 vs 320 vs …) and uint8/float input are detected
   automatically; no code change needed for standard SSD models.

## Known limits (honest)

- **COCO classes only in v1** — 80 everyday classes. There is **no "box"
  class** in COCO: cardboard boxes are typically detected as `suitcase`,
  `book`, or `refrigerator` depending on shape. Use a custom model for
  inventory SKUs.
- **Tracking is IOU-based** — slow, steady camera movement gives the best
  session totals. Fast pans can make the same object get a fresh ID
  (over-counting). The per-frame NMS + tracker also means heavily overlapping
  identical objects may merge into one.
- **Quantized model accuracy** — the 4 MB uint8 model is fast but less accurate
  than large float models; the confidence slider + low-confidence badge are
  there for exactly this reason.
- GPU delegate is attempted but quantized models often run fastest on CPU/NNAPI;
  the fallback chain handles this automatically.

## Roadmap (stubs, not half-features)

`repository/AuthRepository.kt` and `repository/CloudSyncRepository.kt` define
clean interfaces with no-op implementations — accounts, cloud sync, team
workspaces, subscriptions and API access plug in there later without touching
the UI. The CSV exporter is already wired (History → Export CSV).

## License

Built for Zenith AI. Model © its respective owners (TF Hub, Apache 2.0).
