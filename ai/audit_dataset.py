"""Reject exact byte/pixel leakage across train/valid/test. Does not certify patient independence."""
from __future__ import annotations
import argparse
import hashlib
import json
from collections import Counter, defaultdict
from pathlib import Path
from PIL import Image
from train_classify import list_samples, CLASSES


def audit(root: Path) -> dict:
    hashes = defaultdict(list)
    counts = {}
    fingerprint = hashlib.sha256()
    for split in ("train", "valid", "test"):
        if not (root / split).is_dir():
            if split == "test": continue
            raise ValueError(f"Missing split: {split}")
        rows = list_samples(root, split)
        counter = Counter(CLASSES[label] for _, label in rows)
        if any(counter[label] == 0 for label in CLASSES):
            raise ValueError(f"Missing class in {split}: {counter}")
        counts[split] = dict(counter)
        for path, label in rows:
            with Image.open(path) as source:
                image = source.convert("RGB")
                digest = hashlib.sha256(str(image.size).encode() + image.tobytes()).hexdigest()
            hashes[digest].append({"split": split, "label": CLASSES[label], "file": str(path.relative_to(root))})
            fingerprint.update(f"{split}/{path.relative_to(root)}:{digest}:{label}\n".encode())
    leaks = [items for items in hashes.values() if len({i["split"] for i in items}) > 1]
    conflicts = [items for items in hashes.values() if len({i["label"] for i in items}) > 1]
    return {"counts": counts, "pixel_duplicate_cross_split_groups": len(leaks),
            "conflicting_pixel_label_groups": len(conflicts), "examples": (leaks + conflicts)[:10],
            "fingerprint": fingerprint.hexdigest(),
            "limitation": "No proof of patient/source independence or absence of augmented near duplicates"}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    result = audit(args.data)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps(result, indent=2))
    if result["pixel_duplicate_cross_split_groups"] or result["conflicting_pixel_label_groups"]:
        raise SystemExit("Dataset leakage/conflicting labels: do not train")

if __name__ == "__main__": main()
