# Coffee Diseases Detection (Mobile)

Android app for detecting coffee plant diseases using on-device TensorFlow Lite, with Firebase backend (Auth, Firestore, Cloud Functions).

**Repository:** [github.com/ObracHope/CoffeeDiseasesDetection](https://github.com/ObracHope/CoffeeDiseasesDetection)  
**Web Admin Panel:** [github.com/ObracHope/coffee-disease-detection-web](https://github.com/ObracHope/coffee-disease-detection-web)

## Project structure

| Path | Description |
|------|-------------|
| `app/` | Android application (Java) |
| `functions/` | Firebase Cloud Functions (Node.js) |
| `training/` | ML training scripts and model export |
| `scripts/` | Firebase admin setup utilities |
| `firestore.rules` | Firestore security rules |

## Requirements

- Android Studio (AGP 8.7+, JDK 17+)
- Node.js 18+ (Firebase Functions)
- Python 3.11+ with TensorFlow (model training)

## Android setup

1. Clone the repository.
2. Add `app/google-services.json` from your Firebase console.
3. Open in Android Studio and sync Gradle.
4. Run on device/emulator: `./gradlew assembleDebug`

## Firebase Functions

```bash
cd functions
npm install
firebase deploy --only functions
```

## Model training

See `training/README.md`. Place class folders under `dataset/` locally (not committed — too large for Git).

## Author

ObracHope — Obrachope@gmail.com
