"""
Download extra images from Wikimedia Commons for weak classes (especially IsNotCoffee).
"""
import argparse
import hashlib
import json
import time
import urllib.parse
import urllib.request
from pathlib import Path

USER_AGENT = "CoffeeDiseaseDetection/1.0 (educational; contact: local-dev)"
COMMONS_API = "https://commons.wikimedia.org/w/api.php"

# Search queries per class — mix of coffee diseases and clear non-coffee subjects.
CLASS_QUERIES = {
    "IsNotCoffee": [
        "tomato plant leaf",
        "banana fruit",
        "car automobile",
        "human face portrait",
        "dog animal",
        "rice field",
        "maize corn plant",
        "tea plantation",
        "building facade",
        "smartphone",
    ],
    "Wilt": [
        "coffee wilt disease",
        "coffee plant wilting",
        "coffea leaf yellow",
    ],
    "RootRot": [
        "coffee root rot",
        "plant root disease",
    ],
    "BerryDisease": [
        "coffee berry disease",
        "coffea berry anthracnose",
    ],
}


def commons_image_urls(query: str, limit: int) -> list[str]:
    params = urllib.parse.urlencode(
        {
            "action": "query",
            "format": "json",
            "generator": "search",
            "gsrsearch": f'filetype:bitmap {query}',
            "gsrlimit": str(min(limit, 50)),
            "prop": "imageinfo",
            "iiprop": "url|mime",
            "iiurlwidth": "400",
        }
    )
    req = urllib.request.Request(f"{COMMONS_API}?{params}", headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=30) as resp:
        data = json.loads(resp.read().decode("utf-8"))

    pages = data.get("query", {}).get("pages", {})
    urls = []
    for page in pages.values():
        infos = page.get("imageinfo") or []
        if not infos:
            continue
        info = infos[0]
        mime = info.get("mime", "")
        if not mime.startswith("image/"):
            continue
        url = info.get("thumburl") or info.get("url")
        if url:
            urls.append(url)
    return urls


def download_file(url: str, dest: Path) -> bool:
    try:
        req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(req, timeout=45) as resp:
            content = resp.read()
        if len(content) < 3000:
            return False
        dest.write_bytes(content)
        return True
    except Exception:
        return False


def download_for_class(out_dir: Path, class_name: str, target: int, per_query: int):
    out_dir.mkdir(parents=True, exist_ok=True)
    existing = {p.stem for p in out_dir.glob("*") if p.suffix.lower() in {".jpg", ".jpeg", ".png", ".webp"}}
    saved = 0
    queries = CLASS_QUERIES.get(class_name, [class_name.replace("_", " ")])

    for query in queries:
        if saved >= target:
            break
        try:
            urls = commons_image_urls(query, per_query)
        except Exception as exc:
            print(f"  [{class_name}] search failed '{query}': {exc}")
            time.sleep(1)
            continue

        for url in urls:
            if saved >= target:
                break
            digest = hashlib.md5(url.encode()).hexdigest()[:12]
            name = f"web_{digest}.jpg"
            if digest in existing or (out_dir / name).exists():
                continue
            dest = out_dir / name
            if download_file(url, dest):
                existing.add(digest)
                saved += 1
                print(f"  [{class_name}] {saved}/{target} <- {query}")
            time.sleep(0.25)

    print(f"[{class_name}] downloaded {saved} new images -> {out_dir}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset-train-dir", required=True, help="e.g. dataset/train")
    parser.add_argument("--not-coffee-count", type=int, default=120)
    parser.add_argument("--wilt-count", type=int, default=80)
    parser.add_argument("--root-rot-count", type=int, default=60)
    parser.add_argument("--berry-count", type=int, default=60)
    parser.add_argument("--per-query", type=int, default=8)
    args = parser.parse_args()

    train_root = Path(args.dataset_train_dir)
    targets = {
        "IsNotCoffee": args.not_coffee_count,
        "Wilt": args.wilt_count,
        "RootRot": args.root_rot_count,
        "BerryDisease": args.berry_count,
    }
    for class_name, target in targets.items():
        download_for_class(train_root / class_name, class_name, target, args.per_query)


if __name__ == "__main__":
    main()
