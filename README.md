# 🏭 Industry 4.0 AI-Powered Visual Quality Inspection System

[![Platform](https://img.shields.io/badge/Platform-Android%2010%2B%20%28API%2029%2B%29-blue.svg)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin%20%7C%20TypeScript-purple.svg)](https://kotlinlang.org)
[![UI Framework](https://img.shields.io/badge/UI-Jetpack%20Compose%20(Material%203)-green.svg)](https://developer.android.com/jetpack/compose)
[![AI Engine](https://img.shields.io/badge/AI-ONNX%20Runtime%20Mobile-orange.svg)](https://onnxruntime.ai)
[![Model](https://img.shields.io/badge/Model-YOLO11n%20%28Nano%29-red.svg)](https://docs.ultralytics.com)
[![Architecture](https://img.shields.io/badge/Architecture-MVVM%20%2B%20Clean-lightgrey.svg)](https://developer.android.com/topic/architecture)
[![Code Quality](https://img.shields.io/badge/ESLint-Verified%20Clean-success.svg)](https://eslint.org)

An industrial mobile edge-AI visual inspection application and central quality telemetry hub engineered for real-time manufacturing surface defect detection. Built strictly for offline, privacy-first on-device execution using smartphone hardware.

---

## 📌 Table of Contents

1. [System Architecture](#1-system-architecture)
2. [Core Features](#2-core-features)
3. [Model & Dataset Specifications](#3-model--dataset-specifications)
4. [Supplying Your Custom Trained Model](#4-supplying-your-custom-trained-model)
5. [Android Application Setup & Build Guide](#5-android-application-setup--build-guide)
6. [Backend Quality Hub Setup & Execution](#6-backend-quality-hub-setup--execution)
7. [Security, Privacy & Code Protection](#7-security-privacy--code-protection)
8. [Testing & Verification](#8-testing--verification)

---

## 1. System Architecture

The application implements a clean **MVVM (Model-View-ViewModel)** layered architecture with hardware-isolated AI inference pipelines:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Jetpack Compose UI Layer                        │
│  Dashboard │ Live Camera │ Image Upload │ History │ Performance │ Config │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │ StateFlow / Coroutines
┌────────────────────────────────────▼───────────────────────────────────┐
│                           ViewModel Layer                              │
│  InspectionViewModel │ DashboardViewModel │ SettingsViewModel │ ...     │
└───────────────────┬────────────────────────────────┬───────────────────┘
                    │                                │
┌───────────────────▼──────────────┐   ┌─────────────▼───────────────────┐
│       AI Inference Pipeline      │   │          Data Layer             │
│  - ImagePreprocessor (Letterbox) │   │  - AppDatabase (Room SQLite)    │
│  - InferenceEngine (ONNX Runtime)│   │  - InspectionRepository         │
│  - YoloPostProcessor (NMS)       │   │  - Internal File Storage        │
│  - ModelManager (Lifecycle & SAF)│   │  - ExportManager (SAF & Share)  │
└──────────────────────────────────┘   └─────────────────────────────────┘
```

---

## 2. Core Features

### 🟢 Feature A: Home Dashboard
- Real-time model status indicator (`MODEL ACTIVE` / `NOT LOADED`).
- Engine telemetry: Active provider (CPU / NNAPI), input resolution, defect class count.
- Key Performance Indicators: Total inspections performed, total defects flagged.
- Direct navigation hub to Camera, Image Testing, History, Performance, and Settings.
- Recent inspections list with status badges and latencies.

### 🎥 Feature B: Live Camera Inspection
- Real-time camera preview powered by **Android CameraX**.
- Continuous frame acquisition with non-blocking coroutines and thread-safe frame throttling (default 100 ms interval to prevent CPU thermal throttling).
- Real-time `BoundingBoxOverlay` rendering class-specific high-contrast bounding boxes, defect labels, and confidence tags.
- Live Industrial HUD displaying:
  - Defect class & confidence %
  - Model inference latency (ms)
  - End-to-end processing latency (ms)
  - Real-time FPS
  - Defect count
  - Overall inspection status:
    - `DEFECT DETECTED` (Red alert badge)
    - `NO TRAINED DEFECT DETECTED` (Green status badge — strictly compliant with Industry 4.0 QA standards, never falsely claiming guaranteed defect-free)
    - `ERROR` (Amber alert badge)
- Interactive controls: Pause/Resume Inspection, Flashlight/Torch toggle, and Snapshot Capture & Save.

### 🖼️ Feature C: Image Upload Inspection
- Photo picker / file selector supporting **JPG, JPEG, PNG, and WebP**.
- Runs local ONNX inference on imported high-resolution manufacturing images.
- Displays the annotated component with bounding boxes and defect breakdowns.
- One-click "Save Record" to store in local Room database and internal storage.

### 📜 Feature D & E: Inspection History & Room Database
- Local SQLite persistence via Room Database (`InspectionEntity`, `InspectionDao`).
- Filter records by status: `All`, `Defects Only`, `Normal Only`.
- Inspection detail screen with full-size annotated image view, defect coordinates `[left, top, right, bottom]`, confidence percentages, and timestamps.
- Record management: Delete individual records or clear complete history.

### 📊 Feature F: Export Reports
- Export complete inspection history to **CSV spreadsheet format** (`Inspection_Report_<timestamp>.csv`) via Android `FileProvider` and system share sheet.
- Export textual inspection certificates for single inspections.
- Share annotated defect images directly with plant operators and QA managers.

### ⚡ Feature G: Edge AI Performance Monitoring
- Precise breakdown between model inference latency and total end-to-end pipeline latency.
- Statistical tracking: Minimum, Maximum, and Average inference latency across inspection sessions.
- Real-time device memory footprint (Used JVM Heap, Free Heap, Total Allocated Heap).
- Hardware execution provider verification (CPU with SIMD / XNNPACK vs NNAPI NPU accelerator).

### ⚙️ Feature H: Model Configuration & Hot-Swapping
- **Confidence Threshold Slider**: Configurable from 5% (0.05) to 95% (0.95), defaulting to 25% (0.25). Immediately affects predictions without touching model weights.
- **NMS IoU Threshold Slider**: Configurable from 10% (0.10) to 90% (0.90), defaulting to 45% (0.45).
- **Execution Provider Selector**: Toggle between CPU (XNNPACK) and NNAPI with automatic fallback.
- **Frame Throttling Slider**: Configurable from 50 ms to 500 ms.
- **Model Hot-Swap**: Import custom ONNX models and custom class labels (`.txt` or JSON) via Android SAF.
- **Factory Reset**: Revert to the bundled default model at any time.

---

## 3. Model & Dataset Specifications

- **Architecture:** YOLO11n (YOLO11 Nano object detection).
- **Dataset:** GC10-DET (GC10-HF steel manufacturing surface defects).
- **Input Tensor:** `[1, 3, 640, 640]` Planar RGB Float32, normalized to $[0.0, 1.0]$.
- **Output Tensor:** `[1, 14, 8400]` (4 box coordinates $[cx, cy, w, h]$ + 10 defect class scores across 8,400 anchors).
- **The 10 GC10-DET Surface Defect Classes:**
  1. `crease`
  2. `crescent_gap`
  3. `inclusion`
  4. `oil_spot`
  5. `punching_hole`
  6. `rolled_pit`
  7. `silk_spot`
  8. `waist_folding`
  9. `water_spot`
  10. `welding_line`

---

## 4. Supplying Your Custom Trained Model

The application is engineered to accept your custom trained model seamlessly through multiple methods:

### Method 1: In-App Model Import (Recommended on Device)
1. Transfer your custom model file (`yolo_detector_best.onnx`) and labels file (`labels.txt` or `classes.json`) to your phone (via USB, Downloads, or Drive).
2. Open the application and navigate to **Settings** (top-right gear icon).
3. Under **Active Model Information**, tap **"Import Custom ONNX Model (.onnx)"**.
4. Select your file using the Android system file picker.
5. The application performs compatibility checks, validates input/output tensor shapes, copies the model to internal app storage (`context.filesDir/models/`), and reloads the inference session automatically.
6. (Optional) Tap **"Import Custom Labels (.txt / .json)"** if your class names differ from the standard 10 GC10-DET classes.

### Method 2: Bundling Model into Application Assets Before Compilation
1. Export your trained PyTorch checkpoint `yolo_detector_best.pt` to ONNX:
   ```bash
   yolo export model=yolo_detector_best.pt format=onnx imgsz=640 opset=12 dynamic=False
   ```
2. Copy the resulting `.onnx` file to the Android assets directory:
   ```
   app/src/main/assets/models/yolo_detector.onnx
   ```
3. Update `app/src/main/assets/models/labels.txt` with your ordered class names (one class per line).
4. Rebuild the APK.

---

## 5. Android Application Setup & Build Guide

### Prerequisites
- **Android Studio:** Hedgehog (2023.1.1) / Iguana / Jellyfish or newer.
- **JDK:** Java 17 LTS (Amazon Corretto or OpenJDK 17).
- **Target OS:** Android 10 (API Level 29) or higher.

### Opening the Project in Android Studio
1. Launch Android Studio.
2. Select **Open** and choose the directory `frontend_aisoc`.
3. Allow Android Studio to sync the Gradle project.
4. Connect a physical Android smartphone running Android 10+ via USB with **USB Debugging** enabled.
5. Select the target device in the run configuration and click **Run (Shift + F10)**.

### Building APK via Command Line
Run the following commands from the project root:

- **Build Debug APK:**
  ```powershell
  .\gradlew assembleDebug
  ```
  The generated APK will be at:
  `app/build/outputs/apk/debug/app-debug.apk`

- **Build Release APK (Optimized, Minified & Obfuscated):**
  ```powershell
  .\gradlew assembleRelease
  ```
  The generated APK will be at:
  `app/build/outputs/apk/release/app-release.apk`

---

## 6. Backend Quality Hub Setup & Execution

An enterprise Industry 4.0 Central Quality Telemetry & Management Hub backend is included under `backend/`.

### Tech Stack
- Node.js & TypeScript (ES2022)
- Express with Zod validation
- Security: Helmet, CORS, Express-Rate-Limit
- Code Quality: ESLint 9+ flat configuration

### Installation & Execution
```bash
cd backend
npm install
```

### Running ESLint Checks
```bash
npm run lint
```
*Result: Clean verification with 0 errors and 0 warnings.*

### Compiling TypeScript
```bash
npm run build
```

### Running Backend Unit & Security Tests
```bash
npm test
```
*Result: 5 test suites covering schema validation, telemetry ingestion, duplicate prevention, and analytics aggregation.*

### Starting the Quality Hub Server
```bash
npm start
```
Default server port: `http://localhost:3000`

### API Endpoints
- `GET /api/v1/health`: System health and uptime telemetry.
- `POST /api/v1/inspections`: Ingests edge inspection reports from Android devices.
- `GET /api/v1/inspections`: Queries inspection records with pagination and defect filters.
- `GET /api/v1/inspections/:id`: Fetches a single inspection report by UUID.
- `GET /api/v1/analytics/defects`: Aggregated defect frequency and failure rate analytics.
- `GET /api/v1/models/active`: Active model version, recommended thresholds, and architecture specs.

---

## 7. Security, Privacy & Code Protection

- **100% Offline Guarantee:** Core defect detection runs entirely on-device via ONNX Runtime without making external network calls.
- **Zero Unnecessary Permissions:** The Android application does not request `android.permission.INTERNET` in its manifest, guaranteeing that manufacturing images never leave the plant floor.
- **ProGuard / R8 Obfuscation:** Configured in `app/proguard-rules.pro` to:
  - Obfuscate application logic and model pipelines.
  - Strip verbose logs (`Log.d`, `Log.v`) in production builds to prevent diagnostic data leakage.
  - Preserve ONNX Runtime JNI entry points and Room database tables.
- **Secure File Sharing:** All exported CSV logs and images use Android `FileProvider` (`androidx.core.content.FileProvider`) to prevent external exposure of internal app directories.
- **Backend Hardening:** Implements Helmet security headers (Content Security Policy, HSTS, X-Frame-Options), strict rate-limiting, and sanitized error responses with zero stack trace leakage.

---

## 8. Testing & Verification

### Android Unit Tests (`app/src/test/`)
- `YoloPostProcessorTest.kt`:
  - `testEmptyDetectionsWhenScoresBelowThreshold`: Verifies that scores below threshold yield zero false positives.
  - `testSingleDefectDetection`: Validates coordinate decoding, class mapping, and confidence scores.
  - `testNmsSuppressionOfOverlappingBoxes`: Verifies Non-Maximum Suppression eliminates redundant candidate anchors.
  - `testMultipleDistinctDefectsRetained`: Verifies multi-defect detection in a single frame.

### Backend Tests (`backend/src/tests/`)
- Ingestion schema validation with Zod.
- Conflict detection for duplicate inspection UUIDs.
- Defect frequency breakdown and statistical aggregation calculations.
- Clean ESLint analysis with strict type safety.
