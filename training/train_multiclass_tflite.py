import argparse
import json
import os
from pathlib import Path

import numpy as np
import tensorflow as tf
from tensorflow.keras import layers, models
from tensorflow.keras.applications import MobileNetV2, EfficientNetB0
from tensorflow.keras.callbacks import EarlyStopping, ModelCheckpoint, ReduceLROnPlateau
from tensorflow.keras.preprocessing import image_dataset_from_directory


IMG_SIZE = 224
AUTOTUNE = tf.data.AUTOTUNE
CLASS_NAMES = [
    "Healthy",
    "Rust",
    "BerryDisease",
    "Wilt",
    "LeafMiner",
    "RootRot",
    "IsNotCoffee",
]


def parse_args():
    parser = argparse.ArgumentParser(description="Train Coffee Disease 7-class TFLite model")
    parser.add_argument("--dataset-dir", required=True, help="Path to dataset root (flat or train/val split)")
    parser.add_argument("--output-dir", default="training/output", help="Path for model artifacts")
    parser.add_argument("--epochs", type=int, default=30)
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--learning-rate", type=float, default=1e-3)
    parser.add_argument("--backbone", choices=["mobilenetv2", "efficientnetlite0"], default="mobilenetv2")
    return parser.parse_args()


def ensure_structure(dataset_dir: Path):
    train_root = dataset_dir / "train"
    if train_root.exists():
        missing = [name for name in CLASS_NAMES if not (train_root / name).exists()]
        if missing:
            for name in missing:
                (train_root / name).mkdir(parents=True, exist_ok=True)
            print(f"Created empty class folders: {missing}")
        return
    missing = [name for name in CLASS_NAMES if not (dataset_dir / name).exists()]
    if missing:
        raise FileNotFoundError(f"Missing classes in dataset: {missing}")


def make_datasets(dataset_dir: Path, batch_size: int):
    train_root = dataset_dir / "train"
    val_root = dataset_dir / "validation"

    if train_root.exists():
        val_source = val_root if val_root.exists() else train_root
        train_ds = image_dataset_from_directory(
            train_root,
            labels="inferred",
            label_mode="int",
            class_names=CLASS_NAMES,
            image_size=(IMG_SIZE, IMG_SIZE),
            batch_size=batch_size,
            shuffle=True,
            seed=42,
        )
        val_ds = image_dataset_from_directory(
            val_source,
            labels="inferred",
            label_mode="int",
            class_names=CLASS_NAMES,
            image_size=(IMG_SIZE, IMG_SIZE),
            batch_size=batch_size,
            shuffle=False,
        )
        print(f"Using split layout: train={train_root} val={val_source}")
        return train_ds.prefetch(AUTOTUNE), val_ds.prefetch(AUTOTUNE)

    train_ds = image_dataset_from_directory(
        dataset_dir,
        labels="inferred",
        label_mode="int",
        class_names=CLASS_NAMES,
        validation_split=0.2,
        subset="training",
        seed=42,
        image_size=(IMG_SIZE, IMG_SIZE),
        batch_size=batch_size,
    )
    val_ds = image_dataset_from_directory(
        dataset_dir,
        labels="inferred",
        label_mode="int",
        class_names=CLASS_NAMES,
        validation_split=0.2,
        subset="validation",
        seed=42,
        image_size=(IMG_SIZE, IMG_SIZE),
        batch_size=batch_size,
    )
    return train_ds.prefetch(AUTOTUNE), val_ds.prefetch(AUTOTUNE)


def class_weights_from_dataset(dataset):
    counts = np.zeros(len(CLASS_NAMES), dtype=np.float64)
    for _, labels in dataset.unbatch():
        counts[int(labels.numpy())] += 1
    max_count = counts.max() if counts.max() > 0 else 1.0
    return {idx: float(max_count / max(1.0, count)) for idx, count in enumerate(counts)}


def build_model(backbone: str, learning_rate: float):
    data_augmentation = tf.keras.Sequential(
        [
            layers.RandomFlip("horizontal"),
            layers.RandomRotation(0.10),
            layers.RandomZoom(0.15),
            layers.RandomContrast(0.10),
            layers.RandomBrightness(0.10),
        ],
        name="augmentation",
    )

    if backbone == "efficientnetlite0":
        base = EfficientNetB0(include_top=False, weights="imagenet", input_shape=(IMG_SIZE, IMG_SIZE, 3))
    else:
        base = MobileNetV2(include_top=False, weights="imagenet", input_shape=(IMG_SIZE, IMG_SIZE, 3))

    base.trainable = False
    inputs = layers.Input(shape=(IMG_SIZE, IMG_SIZE, 3))
    x = data_augmentation(inputs)
    x = layers.Rescaling(1.0 / 255)(x)
    x = base(x, training=False)
    x = layers.GlobalAveragePooling2D()(x)
    x = layers.Dropout(0.25)(x)
    outputs = layers.Dense(len(CLASS_NAMES), activation="softmax")(x)
    model = models.Model(inputs, outputs)

    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=learning_rate),
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"],
    )
    return model, base


def fine_tune(model, base, train_ds, val_ds, epochs):
    base.trainable = True
    for layer in base.layers[:-30]:
        layer.trainable = False
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=1e-5),
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"],
    )
    return model.fit(train_ds, validation_data=val_ds, epochs=max(5, epochs // 3), verbose=1)


def export_tflite(model, output_dir: Path):
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.float16]
    tflite_model = converter.convert()
    model_path = output_dir / "coffee_disease_model.tflite"
    model_path.write_bytes(tflite_model)
    return model_path


def main():
    args = parse_args()
    dataset_dir = Path(args.dataset_dir)
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    ensure_structure(dataset_dir)
    train_ds, val_ds = make_datasets(dataset_dir, args.batch_size)
    weights = class_weights_from_dataset(train_ds)
    model, base = build_model(args.backbone, args.learning_rate)

    callbacks = [
        EarlyStopping(patience=6, restore_best_weights=True, monitor="val_accuracy"),
        ReduceLROnPlateau(patience=3, factor=0.5, monitor="val_loss"),
        ModelCheckpoint(str(output_dir / "best_model.keras"), save_best_only=True, monitor="val_accuracy"),
    ]

    history_head = model.fit(
        train_ds,
        validation_data=val_ds,
        epochs=args.epochs,
        class_weight=weights,
        callbacks=callbacks,
        verbose=1,
    )
    history_tune = fine_tune(model, base, train_ds, val_ds, args.epochs)
    eval_loss, eval_acc = model.evaluate(val_ds, verbose=0)

    keras_path = output_dir / "coffee_disease_model.keras"
    model.save(keras_path)
    tflite_path = export_tflite(model, output_dir)

    metadata = {
        "classes": CLASS_NAMES,
        "image_size": [IMG_SIZE, IMG_SIZE],
        "train_split": 0.8,
        "validation_split": 0.2,
        "val_accuracy": float(eval_acc),
        "val_loss": float(eval_loss),
        "head_epochs_ran": len(history_head.history.get("loss", [])),
        "finetune_epochs_ran": len(history_tune.history.get("loss", [])),
        "tflite_model": str(tflite_path),
        "keras_model": str(keras_path),
    }
    (output_dir / "model_metadata.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")

    print("Training complete")
    print(json.dumps(metadata, indent=2))


if __name__ == "__main__":
    main()
