"""
Merge dataset/train + dataset/validation and re-split 85/15 per class.
Run after download_web_images.py to balance weak classes.
"""
import argparse
import random
import shutil
from pathlib import Path

CLASS_NAMES = [
    "Healthy",
    "Rust",
    "BerryDisease",
    "Wilt",
    "LeafMiner",
    "RootRot",
    "IsNotCoffee",
]

IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".webp", ".bmp", ".JPG", ".JPEG", ".PNG", ".WEBP"}


def list_images(folder: Path) -> list[Path]:
    if not folder.exists():
        return []
    return [p for p in folder.rglob("*") if p.is_file() and p.suffix in IMAGE_EXTS]


def unique_dest(dest_dir: Path, src: Path) -> Path:
    dest_dir.mkdir(parents=True, exist_ok=True)
    candidate = dest_dir / src.name
    n = 1
    while candidate.exists():
        candidate = dest_dir / f"{src.stem}_{n}{src.suffix}"
        n += 1
    return candidate


def collect_all(dataset_root: Path) -> dict[str, list[Path]]:
    pool: dict[str, list[Path]] = {c: [] for c in CLASS_NAMES}
    for split in ("train", "validation"):
        split_dir = dataset_root / split
        if not split_dir.exists():
            continue
        for class_name in CLASS_NAMES:
            pool[class_name].extend(list_images(split_dir / class_name))

    for class_name in CLASS_NAMES:
        seen = set()
        unique = []
        for p in pool[class_name]:
            key = str(p.resolve())
            if key not in seen:
                seen.add(key)
                unique.append(p)
        pool[class_name] = unique
    return pool


def resplit(pool: dict[str, list[Path]], train_dir: Path, val_dir: Path, val_ratio: float, seed: int):
    rng = random.Random(seed)
    for class_name in CLASS_NAMES:
        (train_dir / class_name).mkdir(parents=True, exist_ok=True)
        (val_dir / class_name).mkdir(parents=True, exist_ok=True)

        files = pool[class_name]
        rng.shuffle(files)
        if not files:
            print(f"  WARNING: no images for {class_name}")
            continue

        if len(files) == 1:
            shutil.copy2(files[0], unique_dest(train_dir / class_name, files[0]))
            print(f"  {class_name}: train=1 val=0")
            continue

        n_val = max(1, int(len(files) * val_ratio))
        n_val = min(n_val, len(files) - 1)
        val_files = files[:n_val]
        train_files = files[n_val:]

        for src in train_files:
            shutil.copy2(src, unique_dest(train_dir / class_name, src))
        for src in val_files:
            shutil.copy2(src, unique_dest(val_dir / class_name, src))
        print(f"  {class_name}: train={len(train_files)} val={len(val_files)}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset-root", required=True)
    parser.add_argument("--val-ratio", type=float, default=0.15)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    root = Path(args.dataset_root).resolve()
    backup = root / "_backup_original"
    prepared = root / "_prepared"

    if prepared.exists():
        shutil.rmtree(prepared)
    prepared.mkdir()

    if not backup.exists():
        print(f"Backing up original splits to {backup}")
        if (root / "train").exists():
            shutil.copytree(root / "train", backup / "train")
        if (root / "validation").exists():
            shutil.copytree(root / "validation", backup / "validation")

    print("Collecting all images ...")
    pool = collect_all(root)
    for c in CLASS_NAMES:
        print(f"  pool {c}: {len(pool[c])}")

    train_out = prepared / "train"
    val_out = prepared / "validation"
    print("Re-splitting ...")
    resplit(pool, train_out, val_out, args.val_ratio, args.seed)

    if (root / "train").exists():
        shutil.rmtree(root / "train")
    if (root / "validation").exists():
        shutil.rmtree(root / "validation")
    shutil.move(str(train_out), str(root / "train"))
    shutil.move(str(val_out), str(root / "validation"))
    shutil.rmtree(prepared)
    print("Dataset ready at", root)


if __name__ == "__main__":
    main()
