# Coffee Disease Detection – Training Samples (Supervised ML)

This folder documents sample images used (or to be used) for training the coffee disease detection model. The app uses **supervised machine learning** to distinguish:

1. **Not coffee** – reject non-coffee images  
2. **Coffee – Healthy** – green leaves, no disease  
3. **Coffee – Diseased** – with one of: Coffee Leaf Rust, Cercospora Leaf Brown Spot, Cercospora Fruit Spot, Sooty Mold Fungus, Anthracnose, Coffee Berry Disease, Seedling Wilt  

## Directory structure (recommended)

```
training_samples/
  README.md           (this file)
  labels.csv          (path, label)
  safe/               (healthy coffee leaves, berries, flowers, plantation)
  unsafe/             (diseased coffee – one subfolder per disease if desired)
  not_coffee/         (optional: non-coffee images for negative samples)
```

## Labels (for labels.csv)

- `healthy` – Safe coffee, no disease  
- `coffee_leaf_rust` – Orange/rust spots on leaves  
- `cercospora_leaf_spot` – Brown spots with yellow halo  
- `cercospora_fruit_spot` – Dark spots on cherries  
- `sooty_mold` – Black sooty coating on leaves  
- `anthracnose` – Yellow/brown spots, necrotic areas  
- `coffee_berry_disease` – Dark lesions on berries  
- `seedling_wilt` – Young seedlings, soft/rotting stems  
- `not_coffee` – Non-coffee images  

## Sample image reference (from provided assets)

Images provided for training reference (safe vs unsafe):

| Type    | Description |
|--------|-------------|
| Safe   | Healthy green coffee leaves, green/red berries, white flowers, plantation views, potted healthy plant |
| Unsafe | Coffee leaf with orange-brown rust spots; Cercospora leaf (brown spots, yellow halo); Cercospora fruit spot (spots on cherries); Sooty mold (black coating); Anthracnose (yellow/brown spots); Leaves with rust in hand |

Copy or place actual image files under `safe/` and `unsafe/` (and optionally `not_coffee/`), then list them in `labels.csv` for training pipelines (e.g. TensorFlow, export to TFLite).

## Current app behavior

The app uses a **heuristic (color-based) fallback** when no TFLite model is present:

- **Not coffee:** Image must show plant-like green/brown/yellow tones; otherwise returns "SORRY THIS IS NOT A COFFEE..."
- **Diseases:** Rust (orange), Cercospora (brown+yellow), Anthracnose (yellow/brown), Sooty mold (dark), Berry disease (dark lesions), **Healthy** (dominant green).

To improve accuracy, train a real model (e.g. CNN on PlantVillage-style dataset or custom dataset from this structure), export to `mobilenet_v2.tflite` (or similar), and place it in `app/src/main/assets/`.
