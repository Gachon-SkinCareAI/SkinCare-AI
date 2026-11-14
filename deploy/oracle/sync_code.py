"""Copy updated code from this git checkout into the running bundle (/srv/skincare).

Only code is replaced: runtime/compose files, ai/, mysql/, migrations/ and,
with --jar, backend/app.jar. private/, models/, distribution/, .env and the
Docker volumes (database, photos, LLM) are never touched.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import shutil

from pack_for_cloud import AI_FILES, AI_PARTS, DEMO, HERE, REPO, RUNTIME_FILES, SERVER_FILES

JAR = REPO / "backend/build/libs/skincare-0.0.1-SNAPSHOT.jar"
IGNORE = shutil.ignore_patterns("__pycache__", "*.pyc", "*.log", ".env", "tests")


def replace_tree(source: Path, target: Path) -> None:
    staging = target.with_name(target.name + ".new")
    shutil.rmtree(staging, ignore_errors=True)
    shutil.copytree(source, staging, ignore=IGNORE)
    shutil.rmtree(target, ignore_errors=True)
    staging.rename(target)


def sync(bundle: Path, jar: bool) -> list[str]:
    if not (bundle / "private").is_dir() or not (bundle / "upload-manifest.json").is_file():
        raise SystemExit(f"{bundle} is not a SkinCare cloud bundle")
    changed = []
    for name in RUNTIME_FILES:
        shutil.copy2(DEMO / name, bundle / name)
    for name in SERVER_FILES:
        shutil.copy2(HERE / name, bundle / name)
    changed.append("runtime files")
    for part in AI_PARTS:
        replace_tree(REPO / "ai" / part, bundle / "ai" / part)
    for name in AI_FILES:
        shutil.copy2(REPO / "ai" / name, bundle / "ai" / name)
    changed.append("ai/")
    for name in ("manage.py", "retention.py"):
        shutil.copy2(REPO / "deploy/mysql" / name, bundle / "mysql" / name)
    replace_tree(REPO / "backend/src/main/resources/db/mysql", bundle / "migrations")
    changed.append("mysql/ migrations/")
    if jar:
        if not JAR.is_file():
            raise SystemExit(f"build the backend first: {JAR} not found")
        shutil.copy2(JAR, bundle / "backend/app.jar")
        changed.append("backend/app.jar")
    return changed


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bundle", default="/srv/skincare")
    parser.add_argument("--jar", action="store_true", help="also replace backend/app.jar with the fresh build")
    args = parser.parse_args()
    print("synced:", ", ".join(sync(Path(args.bundle).resolve(), args.jar)))


if __name__ == "__main__":
    main()
