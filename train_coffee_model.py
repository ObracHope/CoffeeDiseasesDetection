import json
import os
import shutil
from pathlib import Path

import numpy as np
import tensorflow as tf
from tensorflow.keras import layers, models
from tensorflow.keras.applications import MobileNetV2
from tensorflow.keras.callbacks import EarlyStopping, ModelCheckpoint, ReduceLROnPlateau
from tensorflow.keras.preprocessing import image_dataset_from_directory

IMG_SIZE = 224
AUTOTUNE = tf.data.AUTOTUNE

# IMPORTANT: Must match the Android app order in DiseaseDetector.LABELS
CLASS_NAMES = [
    "Healthy",
    "Rust",
    "BerryDisease",
    "Wilt",
    "LeafMiner",
    "RootRot",
    "IsNotCoffee",
]

# CONFIGURATION (can be overridden via env vars)
DATASET_DIR = Path(os.environ.get("DATASET_DIR", "dataset"))
TRAIN_DIR = DATASET_DIR / "train"
VAL_DIR = DATASET_DIR / "validation"
OUTPUT_DIR = Path(os.environ.get("OUTPUT_DIR", "training/output"))
EPOCHS = int(os.environ.get("EPOCHS", "30"))
BATCH_SIZE = int(os.environ.get("BATCH_SIZE", "32"))
LEARNING_RATE = float(os.environ.get("LEARNING_RATE", "0.001"))


def ensure_structure():
    if not TRAIN_DIR.exists():
        raise FileNotFoundError(f"Missing training directory: {TRAIN_DIR}")
    if not VAL_DIR.exists():
        raise FileNotFoundError(f"Missing validation directory: {VAL_DIR}")

    missing_train = [c for c in CLASS_NAMES if not (TRAIN_DIR / c).exists()]
    missing_val = [c for c in CLASS_NAMES if not (VAL_DIR / c).exists()]
    if missing_train or missing_val:
        raise FileNotFoundError(
            "Dataset missing class folders.\n"
            f"Missing in train: {missing_train}\n"
            f"Missing in validation: {missing_val}"
        )


def make_datasets():
    train_ds = image_dataset_from_directory(
        TRAIN_DIR,
        labels="inferred",
        label_mode="int",
        class_names=CLASS_NAMES,
        image_size=(IMG_SIZE, IMG_SIZE),
        batch_size=BATCH_SIZE,
        shuffle=True,
        seed=42,
    ).prefetch(AUTOTUNE)

    val_ds = image_dataset_from_directory(
        VAL_DIR,
        labels="inferred",
        label_mode="int",
        class_names=CLASS_NAMES,
        image_size=(IMG_SIZE, IMG_SIZE),
        batch_size=BATCH_SIZE,
        shuffle=False,
    ).prefetch(AUTOTUNE)

    return train_ds, val_ds


def class_weights_from_dataset(dataset):
    counts = np.zeros(len(CLASS_NAMES), dtype=np.float64)
    for _, labels in dataset.unbatch():
        counts[int(labels.numpy())] += 1
    max_count = counts.max() if counts.max() > 0 else 1.0
    return {i: float(max_count / max(1.0, c)) for i, c in enumerate(counts)}


def build_model():
    augmentation = tf.keras.Sequential(
        [
            layers.RandomFlip("horizontal"),
            layers.RandomRotation(0.10),
            layers.RandomZoom(0.15),
            layers.RandomContrast(0.10),
            layers.RandomBrightness(0.10),
        ],
        name="augmentation",
    )

    base = MobileNetV2(include_top=False, weights="imagenet", input_shape=(IMG_SIZE, IMG_SIZE, 3))
    base.trainable = False

    inputs = layers.Input(shape=(IMG_SIZE, IMG_SIZE, 3))
    x = augmentation(inputs)
    x = layers.Rescaling(1.0 / 255)(x)
    x = base(x, training=False)
    x = layers.GlobalAveragePooling2D()(x)
    x = layers.Dropout(0.25)(x)
    outputs = layers.Dense(len(CLASS_NAMES), activation="softmax")(x)

    model = models.Model(inputs, outputs)
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=LEARNING_RATE),
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"],
    )
    return model, base


def export_tflite(model, output_dir: Path):
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.float16]
    tflite_model = converter.convert()
    out = output_dir / "coffee_disease_model.tflite"
    out.write_bytes(tflite_model)
    return out


def main():
    print("Training Coffee Disease 7-class model...")
    print(f"Dataset train: {TRAIN_DIR}")
    print(f"Dataset validation: {VAL_DIR}")

    ensure_structure()
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    train_ds, val_ds = make_datasets()
    weights = class_weights_from_dataset(train_ds)

    model, base = build_model()

    callbacks = [
        EarlyStopping(patience=6, restore_best_weights=True, monitor="val_accuracy"),
        ReduceLROnPlateau(patience=3, factor=0.5, monitor="val_loss"),
        ModelCheckpoint(str(OUTPUT_DIR / "best_model.keras"), save_best_only=True, monitor="val_accuracy"),
    ]

    history_head = model.fit(train_ds, validation_data=val_ds, epochs=EPOCHS, class_weight=weights, callbacks=callbacks)

    # Light fine-tune
    base.trainable = True
    for layer in base.layers[:-30]:
        layer.trainable = False
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=1e-5),
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"],
    )
    history_tune = model.fit(train_ds, validation_data=val_ds, epochs=max(5, EPOCHS // 3), verbose=1)

    eval_loss, eval_acc = model.evaluate(val_ds, verbose=0)

    keras_path = OUTPUT_DIR / "coffee_disease_model.keras"
    model.save(keras_path)
    tflite_path = export_tflite(model, OUTPUT_DIR)

    # Copy model into Android assets (so the app uses it)
    assets_dir = Path("app/src/main/assets")
    if assets_dir.exists():
        assets_dir.mkdir(parents=True, exist_ok=True)
        shutil.copy2(tflite_path, assets_dir / "coffee_disease_model.tflite")

    metadata = {
        "classes": CLASS_NAMES,
        "image_size": [IMG_SIZE, IMG_SIZE],
        "val_accuracy": float(eval_acc),
        "val_loss": float(eval_loss),
        "head_epochs_ran": len(history_head.history.get("loss", [])),
        "finetune_epochs_ran": len(history_tune.history.get("loss", [])),
        "tflite_model": str(tflite_path),
        "keras_model": str(keras_path),
    }
    (OUTPUT_DIR / "model_metadata.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")

    print("✅ Model exported")
    print(json.dumps(metadata, indent=2))


if __name__ == "__main__":
    main()