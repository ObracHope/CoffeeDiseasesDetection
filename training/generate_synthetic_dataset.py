"""
Generate synthetic coffee leaf images for each class folder.
Used when a real labeled dataset is not available.
"""
import argparse
import random
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

CLASS_NAMES = [
    "Healthy",
    "Rust",
    "BerryDisease",
    "Wilt",
    "LeafMiner",
    "RootRot",
    "IsNotCoffee",
]

IMG_SIZE = 224


def _ellipse_box(rng: random.Random, max_size: int = 80) -> tuple:
    x0 = rng.randint(10, IMG_SIZE - max_size - 10)
    y0 = rng.randint(10, IMG_SIZE - max_size - 10)
    w = rng.randint(20, max_size)
    h = rng.randint(20, max_size)
    return (x0, y0, x0 + w, y0 + h)


def _base_leaf(rng: random.Random) -> Image.Image:
    img = Image.new("RGB", (IMG_SIZE, IMG_SIZE), (34, 80, 40))
    draw = ImageDraw.Draw(img)
    for _ in range(6):
        color = (
            rng.randint(40, 90),
            rng.randint(120, 200),
            rng.randint(30, 80),
        )
        draw.ellipse(_ellipse_box(rng, 90), fill=color)
    return img


def _healthy(rng: random.Random) -> Image.Image:
    img = _base_leaf(rng)
    draw = ImageDraw.Draw(img)
    for _ in range(rng.randint(8, 14)):
        draw.ellipse(
            _ellipse_box(rng, 60),
            fill=(rng.randint(50, 110), rng.randint(150, 220), rng.randint(40, 90)),
        )
    return img


def _rust(rng: random.Random) -> Image.Image:
    img = _healthy(rng)
    draw = ImageDraw.Draw(img)
    for _ in range(rng.randint(25, 45)):
        draw.ellipse(
            _ellipse_box(rng, 25),
            fill=(rng.randint(200, 255), rng.randint(120, 200), rng.randint(20, 80)),
        )
    return img


def _berry(rng: random.Random) -> Image.Image:
    img = _healthy(rng)
    draw = ImageDraw.Draw(img)
    for _ in range(rng.randint(18, 30)):
        draw.ellipse(
            _ellipse_box(rng, 30),
            fill=(rng.randint(10, 40), rng.randint(10, 40), rng.randint(10, 40)),
        )
    return img


def _wilt(rng: random.Random) -> Image.Image:
    img = Image.new("RGB", (IMG_SIZE, IMG_SIZE), (90, 70, 30))
    draw = ImageDraw.Draw(img)
    for _ in range(5):
        draw.ellipse(
            _ellipse_box(rng, 100),
            fill=(rng.randint(120, 180), rng.randint(100, 150), rng.randint(20, 60)),
        )
    return img


def _leaf_miner(rng: random.Random) -> Image.Image:
    img = _healthy(rng)
    draw = ImageDraw.Draw(img)
    for _ in range(rng.randint(10, 18)):
        x = rng.randint(20, 180)
        y = rng.randint(20, 180)
        draw.line((x, y, x + rng.randint(30, 80), y + rng.randint(-10, 10)), fill=(240, 240, 230), width=3)
    return img


def _root_rot(rng: random.Random) -> Image.Image:
    img = _healthy(rng)
    draw = ImageDraw.Draw(img)
    for _ in range(rng.randint(12, 22)):
        draw.ellipse(
            _ellipse_box(rng, 35),
            fill=(rng.randint(160, 220), rng.randint(140, 200), rng.randint(20, 60)),
        )
    return img


def _not_coffee(rng: random.Random) -> Image.Image:
    kind = rng.randint(0, 3)
    if kind == 0:
        img = Image.new("RGB", (IMG_SIZE, IMG_SIZE), (rng.randint(120, 200), rng.randint(150, 220), rng.randint(200, 255)))
        draw = ImageDraw.Draw(img)
        draw.rectangle((20, 120, 200, 210), fill=(120, 120, 120))
    elif kind == 1:
        img = Image.new("RGB", (IMG_SIZE, IMG_SIZE), (rng.randint(180, 240), rng.randint(120, 180), rng.randint(90, 140)))
    elif kind == 2:
        img = Image.new("RGB", (IMG_SIZE, IMG_SIZE), (40, 40, 40))
        draw = ImageDraw.Draw(img)
        for _ in range(8):
            draw.ellipse(
                _ellipse_box(rng, 50),
                fill=(rng.randint(180, 255), rng.randint(20, 80), rng.randint(20, 80)),
            )
    else:
        img = Image.new("RGB", (IMG_SIZE, IMG_SIZE), (rng.randint(20, 80), rng.randint(80, 160), rng.randint(160, 255)))
    return img


GENERATORS = {
    "Healthy": _healthy,
    "Rust": _rust,
    "BerryDisease": _berry,
    "Wilt": _wilt,
    "LeafMiner": _leaf_miner,
    "RootRot": _root_rot,
    "IsNotCoffee": _not_coffee,
}


def generate(dataset_dir: Path, samples_per_class: int, seed: int):
    rng = random.Random(seed)
    for class_name in CLASS_NAMES:
        out_dir = dataset_dir / class_name
        out_dir.mkdir(parents=True, exist_ok=True)
        gen = GENERATORS[class_name]
        for i in range(samples_per_class):
            img = gen(rng)
            arr = np.array(img).astype(np.float32)
            arr += np.random.default_rng(rng.randint(0, 2**31)).uniform(-8, 8, arr.shape)
            arr = np.clip(arr, 0, 255).astype(np.uint8)
            Image.fromarray(arr).save(out_dir / f"{class_name.lower()}_{i:04d}.jpg", quality=90)
    print(f"Generated {samples_per_class} images per class in {dataset_dir}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", default="training/synthetic_dataset")
    parser.add_argument("--samples-per-class", type=int, default=120)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()
    generate(Path(args.output_dir), args.samples_per_class, args.seed)
