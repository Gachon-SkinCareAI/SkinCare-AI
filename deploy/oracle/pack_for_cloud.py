"""Build the Oracle Cloud upload folder from this repo + the SkinCare-AI transfer package.

Run on the PC (Windows or Linux) that holds the transfer package:

    python deploy/oracle/pack_for_cloud.py --transfer C:/Users/me/Desktop/SkinCare-AI-transfer \
        --output C:/Users/me/Desktop/skincare-cloud-upload

The output keeps the EXISTING database credentials, OAuth keys, JWT key, models,
signed APK and fixed-address gateway keys, so the restored DB and the installed
app (v15, https://skincare-ai.skincare-server.workers.dev) keep working.

Not copied: data/images.tar (x86 images; the ARM server rebuilds them),
caddy volumes (the cloud edge does not terminate TLS) and the vision cache volume.
The output contains secrets and personal data: keep it private, never commit it.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import sys

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
DEMO = REPO / "deploy" / "public-demo"
sys.path.insert(0, str(DEMO))
from prepare_bundle import RUNTIME_FILES  # noqa: E402  (single source of truth)

AI_PARTS = ("app", "vision", "qa", "guide", "tracking", "jobs")
AI_FILES = ("train_classify.py", "eval_classify.py", "config.yaml")
VOLUMES = ("database", "maintenance", "local_llm")  # restored on the server
SECRET_TEXT = ("db-root.txt", "db-app.txt", "ai-token.txt", "jwt.txt")
SERVER_FILES = ("setup_server.sh", "restore_volumes.sh", "install_service.sh",
                "skincare-ensure.service", "skincare-ensure.timer", "README.md")
DISTRIBUTION = ("index.html", "SkinCare-AI.apk", "install-qr.png", "release.json")
PUBLIC_ORIGIN = "https://skincare-ai.skincare-server.workers.dev"


def digest(path: Path) -> str:
    value = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(chunk)
    return value.hexdigest()


def lf_copy(source: Path, target: Path, *, strip: bool = False) -> None:
    """Copy text with LF endings. MySQL reads a trailing CR as part of the password."""
    text = source.read_bytes().decode("utf-8-sig").replace("\r\n", "\n").replace("\r", "\n")
    if strip:
        text = text.strip() + "\n"
    target.write_bytes(text.encode("utf-8"))


def require(path: Path) -> Path:
    if not path.exists():
        raise SystemExit(f"missing in transfer package: {path}")
    return path


def pack(transfer: Path, output: Path, skip_volumes: bool) -> dict:
    if output == REPO or output.is_relative_to(REPO):
        raise SystemExit("Keep the private upload folder outside the source repository")
    if output.exists() and any(output.iterdir()):
        raise SystemExit("Use a new empty --output folder; existing files are never replaced")
    release = json.loads(require(transfer / "fixed-live/install/release.json").read_text(encoding="utf-8-sig"))
    if release.get("apiOrigin") != PUBLIC_ORIGIN:
        raise SystemExit(f"fixed-live APK targets {release.get('apiOrigin')}, expected {PUBLIC_ORIGIN}")
    output.mkdir(parents=True, exist_ok=True)

    # 1. Runtime files and AI source (same allowlist as prepare_bundle.py).
    for name in RUNTIME_FILES:
        shutil.copy2(DEMO / name, output / name)
    for part in AI_PARTS:
        shutil.copytree(REPO / "ai" / part, output / "ai" / part,
                        ignore=shutil.ignore_patterns("__pycache__", "*.pyc", "*.log", ".env", "tests"))
    for name in AI_FILES:
        shutil.copy2(REPO / "ai" / name, output / "ai" / name)
    (output / "mysql").mkdir()
    for name in ("manage.py", "retention.py"):
        shutil.copy2(REPO / "deploy/mysql" / name, output / "mysql" / name)
    shutil.copytree(REPO / "backend/src/main/resources/db/mysql", output / "migrations")
    for name in SERVER_FILES:
        shutil.copy2(HERE / name, output / name)
    shutil.copy2(require(transfer / "tools/volume_io.py"), output / "volume_io.py")

    # 2. The backend JAR that matches the backed-up database.
    (output / "backend").mkdir()
    shutil.copy2(require(transfer / "server/updates/backend/app.jar"), output / "backend/app.jar")

    # 3. Vision models.
    (output / "models").mkdir()
    models = {}
    for name in ("detector.pt", "classifier.pt", "energy.json"):
        source = require(transfer / "model-originals" / name)
        shutil.copy2(source, output / "models" / name)
        models[name] = {"sha256": digest(output / "models" / name), "bytes": source.stat().st_size}
    (output / "models/manifest.json").write_text(json.dumps({"files": models}, indent=2), encoding="utf-8")

    # 4. Existing secrets, LF-normalised.
    private = output / "private"
    private.mkdir()
    for name in SECRET_TEXT:
        lf_copy(require(transfer / "private" / name), private / name, strip=True)
    lf_copy(require(transfer / "private/application-secrets.properties"), private / "application-secrets.properties")
    lf_copy(require(transfer / "fixed-live/private/gateway-secrets.json"), private / "gateway-secrets.json")

    # 5. Installed-app download page (served at /download/).
    (output / "distribution").mkdir()
    for name in DISTRIBUTION:
        shutil.copy2(require(transfer / "fixed-live/install" / name), output / "distribution" / name)

    # 6. Compose interpolation values.
    shutil.copy2(DEMO / "cloud.env.example", output / ".env")

    # 7. Volume backups to restore on the server.
    archives = {}
    if not skip_volumes:
        (output / "restore").mkdir()
        for key in VOLUMES:
            source = require(transfer / "data" / f"{key}.tar")
            target = output / "restore" / f"{key}.tar"
            print(f"copying {source.name} ({source.stat().st_size // (1024 * 1024)} MB)...", flush=True)
            shutil.copy2(source, target)
            archives[f"restore/{key}.tar"] = digest(target)

    files = {path.relative_to(output).as_posix(): digest(path)
             for path in sorted(output.rglob("*")) if path.is_file() and not path.is_relative_to(output / "restore")}
    files.update(archives)
    manifest = {"format": "skincare-oracle-upload-v1", "publicOrigin": PUBLIC_ORIGIN,
                "apkVersionCode": release.get("versionCode"), "volumes": list(VOLUMES) if not skip_volumes else [],
                "files": files}
    (output / "upload-manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    total = sum(p.stat().st_size for p in output.rglob("*") if p.is_file())
    return {"packed": True, "output": str(output), "megabytes": total // (1024 * 1024),
            "volumes": manifest["volumes"], "apkVersionCode": manifest["apkVersionCode"]}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--transfer", required=True, help="SkinCare-AI-transfer folder")
    parser.add_argument("--output", required=True, help="new empty folder to upload to the server")
    parser.add_argument("--skip-volumes", action="store_true", help="code/config only (no DB or LLM backup)")
    args = parser.parse_args()
    print(json.dumps(pack(Path(args.transfer).resolve(), Path(args.output).resolve(), args.skip_volumes)))


if __name__ == "__main__":
    main()
