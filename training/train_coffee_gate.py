"""
Train binary Coffee vs NotCoffee gate model (Stage 1).
Uses dataset/train + dataset/validation folder structure.
"""
import argparse
import json
import shutil
from pathlib import Path

import tensorflow as tf
from tensorflow.keras import layers, models
from tensorflow.keras.applications import MobileNetV2
from tensorflow.keras.callbacks import EarlyStopping, ModelCheckpoint, ReduceLROnPlateau
from tensorflow.keras.preprocessing import image_dataset_from_directory

IMG_SIZE = 224
AUTOTUNE = tf.data.AUTOTUNE
COFFEE_CLASSES = ["Healthy", "Rust", "BerryDisease", "Wilt", "LeafMiner", "RootRot"]
NOT_COFFEE_CLASS = "IsNotCoffee"
BINARY_CLASSES = ["Coffee", "NotCoffee"]


def prepare_binary_dirs(dataset_root: Path, staging: Path):
    if staging.exists():
        shutil.rmtree(staging)
    for split in ("train", "validation"):
        for label in BINARY_CLASSES:
            (staging / split / label).mkdir(parents=True, exist_ok=True)

        src_split = dataset_root / split
        if not src_split.exists():
            continue

        for cls in COFFEE_CLASSES:
            src = src_split / cls
            if not src.exists():
                continue
            for img in src.iterdir():
                if img.suffix.lower() in {".jpg", ".jpeg", ".png", ".webp", ".bmp"}:
                    dest_name = f"{cls}_{img.name}"
                    shutil.copy2(img, staging / split / "Coffee" / dest_name)

        src_nc = src_split / NOT_COFFEE_CLASS
        if src_nc.exists():
            for img in src_nc.iterdir():
                if img.suffix.lower() in {".jpg", ".jpeg", ".png", ".webp", ".bmp"}:
                    shutil.copy2(img, staging / split / "NotCoffee" / img.name)

    print(f"Binary staging ready at {staging}")


def make_datasets(staging: Path, batch_size: int):
    train_ds = image_dataset_from_directory(
        staging / "train",
        labels="inferred",
        label_mode="int",
        class_names=BINARY_CLASSES,
        image_size=(IMG_SIZE, IMG_SIZE),
        batch_size=batch_size,
        shuffle=True,
        seed=42,
    )
    val_ds = image_dataset_from_directory(
        staging / "validation",
        labels="inferred",
        label_mode="int",
        class_names=BINARY_CLASSES,
        image_size=(IMG_SIZE, IMG_SIZE),
        batch_size=batch_size,
        shuffle=False,
    )
    return train_ds.prefetch(AUTOTUNE), val_ds.prefetch(AUTOTUNE)


def build_model(learning_rate: float):
    base = MobileNetV2(include_top=False, weights="imagenet", input_shape=(IMG_SIZE, IMG_SIZE, 3))
    base.trainable = False
    inputs = layers.Input(shape=(IMG_SIZE, IMG_SIZE, 3))
    x = layers.Rescaling(1.0 / 255)(inputs)
    x = base(x, training=False)
    x = layers.GlobalAveragePooling2D()(x)
    x = layers.Dropout(0.3)(x)
    outputs = layers.Dense(2, activation="softmax")(x)
    model = models.Model(inputs, outputs)
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=learning_rate),
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"],
    )
    return model, base


def export_tflite(model, output_dir: Path):
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_model = converter.convert()
    path = output_dir / "coffee_gate.tflite"
    path.write_bytes(tflite_model)
    return path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset-root", default="dataset")
    parser.add_argument("--output-dir", default="training/output")
    parser.add_argument("--epochs", type=int, default=12)
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--learning-rate", type=float, default=1e-3)
    parser.add_argument("--skip-prepare", action="store_true")
    args = parser.parse_args()

    root = Path(args.dataset_root)
    output_dir = Path(args.output_dir)
    staging = Path("training/binary_staging")
    output_dir.mkdir(parents=True, exist_ok=True)

    if not args.skip_prepare:
        prepare_binary_dirs(root, staging)

    train_ds, val_ds = make_datasets(staging, args.batch_size)
    model, base = build_model(args.learning_rate)

    callbacks = [
        EarlyStopping(patience=4, restore_best_weights=True, monitor="val_accuracy"),
        ReduceLROnPlateau(patience=2, factor=0.5, monitor="val_loss"),
        ModelCheckpoint(str(output_dir / "coffee_gate_best.keras"), save_best_only=True, monitor="val_accuracy"),
    ]

    model.fit(train_ds, validation_data=val_ds, epochs=args.epochs, callbacks=callbacks, verbose=1)

    base.trainable = True
    for layer in base.layers[:-20]:
        layer.trainable = False
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=1e-5),
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"],
    )
    model.fit(train_ds, validation_data=val_ds, epochs=max(3, args.epochs // 3), verbose=1)

    best_path = output_dir / "coffee_gate_best.keras"
    if best_path.exists():
        model = tf.keras.models.load_model(best_path)

    eval_loss, eval_acc = model.evaluate(val_ds, verbose=0)
    keras_path = output_dir / "coffee_gate.keras"
    model.save(keras_path)
    tflite_path = export_tflite(model, output_dir)

    assets = Path("app/src/main/assets")
    if assets.exists():
        shutil.copy2(tflite_path, assets / "coffee_gate.tflite")

    meta = {
        "classes": BINARY_CLASSES,
        "coffee_index": 0,
        "not_coffee_index": 1,
        "image_size": [IMG_SIZE, IMG_SIZE],
        "input_range": "0-255 float (model has internal Rescaling 1/255)",
        "val_accuracy": float(eval_acc),
        "tflite": str(tflite_path),
    }
    (output_dir / "coffee_gate_metadata.json").write_text(json.dumps(meta, indent=2), encoding="utf-8")
    print(json.dumps(meta, indent=2))


if __name__ == "__main__":
    main()
