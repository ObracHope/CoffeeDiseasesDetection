"""Quick sanity check for TFLite models on dataset samples."""
import numpy as np
from pathlib import Path
import tensorflow as tf
from PIL import Image

LABELS = [
    "Healthy", "Rust", "BerryDisease", "Wilt", "LeafMiner", "RootRot", "IsNotCoffee"
]


def run_model(model_path: str, folder: str, scale: str):
    interp = tf.lite.Interpreter(model_path=model_path)
    interp.allocate_tensors()
    inp = interp.get_input_details()[0]
    out = interp.get_output_details()[0]
    root = Path("dataset/train") / folder
    exts = {".jpg", ".jpeg", ".png", ".webp"}
    imgs = [p for p in root.iterdir() if p.suffix.lower() in exts][:2]
    results = []
    for p in imgs:
        img = Image.open(p).convert("RGB").resize((224, 224))
        arr = np.array(img, dtype=np.float32)
        if scale == "1":
            arr = arr / 255.0
        arr = arr[None, ...]
        interp.set_tensor(inp["index"], arr)
        interp.invoke()
        s = interp.get_tensor(out["index"])[0]
        idx = int(np.argmax(s))
        results.append((p.name, LABELS[idx], float(s[idx])))
    return results


def main():
    models = [
        "app/src/main/assets/coffee_disease_model.tflite",
        "training/output/coffee_disease_model.tflite",
    ]
    folders = ["Healthy", "Rust", "BerryDisease", "LeafMiner", "IsNotCoffee"]
    for mp in models:
        if not Path(mp).exists():
            print("MISSING", mp)
            continue
        print("\n===", mp, "===")
        for folder in folders:
            for scale in ("255", "1"):
                try:
                    r = run_model(mp, folder, scale)
                    print(f"  {folder} scale={scale}: {r}")
                except Exception as e:
                    print(f"  {folder} scale={scale}: ERR {e}")

    gate = Path("app/src/main/assets/coffee_gate.tflite")
    if gate.exists():
        print("\n=== gate ===")
        interp = tf.lite.Interpreter(model_path=str(gate))
        interp.allocate_tensors()
        inp = interp.get_input_details()[0]
        out = interp.get_output_details()[0]
        for folder in ["Healthy", "Rust", "IsNotCoffee"]:
            p = next(Path("dataset/train", folder).glob("*.jpg"), None)
            if p is None:
                p = next(Path("dataset/train", folder).glob("*.png"), None)
            if p is None:
                continue
            img = Image.open(p).convert("RGB").resize((224, 224))
            for scale in ("255", "1"):
                arr = np.array(img, dtype=np.float32)
                if scale == "1":
                    arr = arr / 255.0
                arr = arr[None, ...]
                interp.set_tensor(inp["index"], arr)
                interp.invoke()
                s = interp.get_tensor(out["index"])[0]
                print(f"  {folder} scale={scale}: coffee={float(s[0]):.3f} not={float(s[1]):.3f}")


if __name__ == "__main__":
    main()
