# Build, ML model, and release

## Dataset layout for training

Train with class folders under a single root (see `training/train_multiclass_tflite.py`):

- `Healthy`
- `Rust`
- `BerryDisease`
- `Wilt`
- `LeafMiner`
- `RootRot`
- `IsNotCoffee`

## Train and export TFLite

From `CoffeeDiseasesDetection/training` (or project root with adjusted paths):

```bash
pip install "tensorflow>=2.15,<2.19"
python train_multiclass_tflite.py --dataset-dir /path/to/dataset --output-dir training/output --epochs 30
```

The script writes **`coffee_disease_model.tflite`** under `--output-dir` (see `export_tflite` in `train_multiclass_tflite.py`) plus `coffee_disease_model.keras` and `model_metadata.json`.

## Place the model in the Android app

Copy the generated `.tflite` into:

`app/src/main/assets/coffee_disease_model.tflite`

The Java/Kotlin loader should reference the same filename used in assets.

**Git:** Large binary models are often excluded from Git or tracked with **Git LFS** to avoid bloating the repository. Commit only if your team policy allows; otherwise document local build steps for CI/producers.

## Debug vs release builds

- **Debug:** `.\gradlew assembleDebug` (Windows) or `./gradlew assembleDebug`.
- **Release APK/AAB:** `.\gradlew assembleRelease` or `bundleRelease`.

## Signing (release)

1. Create a keystore **outside** the repo (do not commit `.jks` / passwords).
2. In `app/build.gradle` (or Gradle Kotlin DSL), configure `signingConfigs.release` with `storeFile`, `storePassword`, `keyAlias`, `keyPassword`.
3. Prefer environment variables or `local.properties` (gitignored) for secrets.

Never commit keystores or plaintext passwords.

## Verification

After swapping the model, run inference on a small validation set and smoke-test the classify screen on device/emulator.
