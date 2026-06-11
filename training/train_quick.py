"""Fast training script for synthetic coffee disease dataset."""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "training"))

from train_multiclass_tflite import main as train_main

if __name__ == "__main__":
    dataset = ROOT / "training" / "synthetic_dataset"
    if not dataset.exists():
        print("Run generate_synthetic_dataset.py first")
        sys.exit(1)
    sys.argv = [
        "train_multiclass_tflite.py",
        "--dataset-dir", str(dataset),
        "--output-dir", str(ROOT / "training" / "output"),
        "--epochs", "8",
        "--batch-size", "16",
    ]
    train_main()
