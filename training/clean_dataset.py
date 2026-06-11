"""Remove or re-encode images that break TensorFlow decode."""
import argparse
from pathlib import Path

from PIL import Image

IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".webp", ".bmp", ".JPG", ".JPEG", ".PNG", ".WEBP", ".BMP"}


def clean_root(root: Path):
    removed = 0
    fixed = 0
    for path in root.rglob("*"):
        if not path.is_file() or path.suffix not in IMAGE_EXTS:
            continue
        try:
            with Image.open(path) as img:
                img.verify()
            with Image.open(path) as img:
                rgb = img.convert("RGB")
                rgb.save(path.with_suffix(".jpg"), quality=90)
                if path.suffix.lower() not in {".jpg", ".jpeg"}:
                    path.unlink()
                    fixed += 1
        except Exception:
            path.unlink(missing_ok=True)
            removed += 1
    print(f"{root}: removed={removed} re-encoded={fixed}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset-root", required=True)
    args = parser.parse_args()
    root = Path(args.dataset_root)
    for split in ("train", "validation"):
        split_dir = root / split
        if split_dir.exists():
            clean_root(split_dir)


if __name__ == "__main__":
    main()
