# Coffee Disease Training Pipeline

This folder contains the production training pipeline for the 7-class coffee disease model:

- Healthy
- Rust
- BerryDisease
- Wilt
- LeafMiner
- RootRot
- IsNotCoffee

## Dataset structure

```
dataset/
  Healthy/
  Rust/
  BerryDisease/
  Wilt/
  LeafMiner/
  RootRot/
  IsNotCoffee/
```

Recommended minimum:

- 1000+ images per coffee class
- 2000+ images in `IsNotCoffee`

## Train command

```bash
python training/train_multiclass_tflite.py --dataset-dir dataset --backbone mobilenetv2 --epochs 30
```

Use `--backbone efficientnetlite0` if you want an EfficientNet based training run.

## Outputs

The script writes to `training/output`:

- `coffee_disease_model.keras`
- `coffee_disease_model.tflite`
- `best_model.keras`
- `model_metadata.json`

After validation, copy `coffee_disease_model.tflite` to:

`app/src/main/assets/coffee_disease_model.tflite`
