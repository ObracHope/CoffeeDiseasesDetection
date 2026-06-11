"""
Boost weak classes using:
1. Synthetic generators (IsNotCoffee, Wilt, RootRot, BerryDisease)
2. Color-shifted copies from Healthy/Rust for disease-like classes
"""
import argparse
import random
import shutil
from pathlib import Path

from PIL import Image, ImageEnhance

from generate_synthetic_dataset import CLASS_NAMES, GENERATORS, IMG_SIZE, generate

IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".webp", ".JPG", ".PNG"}


def list_images(folder: Path) -> list[Path]:
    if not folder.exists():
        return []
    return [p for p in folder.iterdir() if p.is_file() and p.suffix in IMAGE_EXTS]


def augment_from_source(src: Path, dest_dir: Path, prefix: str, rng: random.Random):
    img = Image.open(src).convert("RGB").resize((IMG_SIZE, IMG_SIZE))
    variants = [
        lambda i: ImageEnhance.Color(i).enhance(0.4),
        lambda i: ImageEnhance.Brightness(i).enhance(0.7),
        lambda i: ImageEnhance.Contrast(i).enhance(1.3),
    ]
    for idx, fn in enumerate(variants):
        out = fn(img)
        name = f"{prefix}_aug_{idx}.jpg"
        out.save(dest_dir / name, quality=88)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset-train-dir", required=True)
    parser.add_argument("--synthetic-per-class", type=int, default=150)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    train_root = Path(args.dataset_train_dir)
    rng = random.Random(args.seed)

    # Synthetic for all weak generators
    weak = ["IsNotCoffee", "Wilt", "RootRot", "BerryDisease"]
    for class_name in weak:
        out_dir = train_root / class_name
        out_dir.mkdir(parents=True, exist_ok=True)
        gen = GENERATORS[class_name]
        start = len(list_images(out_dir))
        for i in range(args.synthetic_per_class):
            img = gen(rng)
            img.save(out_dir / f"syn_{class_name.lower()}_{start + i:04d}.jpg", quality=90)
        print(f"{class_name}: +{args.synthetic_per_class} synthetic (total ~{start + args.synthetic_per_class})")

    # Augment Wilt/RootRot from Healthy and Rust real images
    healthy = list_images(train_root / "Healthy")
    rust = list_images(train_root / "Rust")
    sources_wilt = (healthy + rust)[: min(200, len(healthy) + len(rust))]
    sources_rot = healthy[: min(120, len(healthy))]

    wilt_dir = train_root / "Wilt"
    rot_dir = train_root / "RootRot"
    wilt_dir.mkdir(parents=True, exist_ok=True)
    rot_dir.mkdir(parents=True, exist_ok=True)

    for i, src in enumerate(sources_wilt):
        augment_from_source(src, wilt_dir, f"wilt_from_{src.stem}", rng)
    for i, src in enumerate(sources_rot):
        augment_from_source(src, rot_dir, f"root_from_{src.stem}", rng)

    print(f"Wilt: augmented from {len(sources_wilt)} sources")
    print(f"RootRot: augmented from {len(sources_rot)} sources")


if __name__ == "__main__":
    main()
