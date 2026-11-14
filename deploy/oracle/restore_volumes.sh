#!/usr/bin/env bash
# Restore the database, maintenance state and Ollama model (qwen3:4b) into Docker
# volumes, then set file permissions. Run ONCE in the upload folder, e.g. /srv/skincare:
#   bash restore_volumes.sh
# Refuses to overwrite a volume that already exists.
set -euo pipefail
cd "$(dirname "$0")"
PROJECT=skincare-public-demo
COMPOSE=(docker compose --env-file .env -f compose.yaml -f compose.cloud.yaml -f compose.llm.yaml -f compose.llm-cpu.yaml)

echo "== verify upload against upload-manifest.json"
# After the first run some private files belong to UID 10001 and cannot be re-read.
[ -f .upload-verified ] && echo "already verified" || python3 - <<'EOF'
import hashlib, json, pathlib, sys
manifest = json.loads(pathlib.Path("upload-manifest.json").read_text())
bad = []
for name, expected in manifest["files"].items():
    path = pathlib.Path(name)
    if name in ("upload-manifest.json",):
        continue
    if not path.is_file():
        bad.append("missing " + name); continue
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    if h.hexdigest() != expected:
        bad.append("changed " + name)
if bad:
    sys.exit("upload check failed:\n  " + "\n  ".join(bad))
print("upload verified:", len(manifest["files"]), "files")
pathlib.Path(".upload-verified").write_text("ok\n")
EOF

echo "== permissions"
chmod 700 private
sudo chown 10001:10001 private/application-secrets.properties private/ai-token.txt
sudo chmod 400 private/application-secrets.properties private/ai-token.txt
chmod 600 private/db-root.txt private/db-app.txt private/jwt.txt private/gateway-secrets.json
chmod 755 models
chmod 644 models/*

echo "== build the maintenance image (used to unpack the archives safely)"
"${COMPOSE[@]}" build maintenance
# Compose names a build-only service's image <project>-<service>. Do not use
# "config --images": it can print the db service's mysql image, which lacks python3.
IMAGE="${PROJECT}-maintenance"
docker image inspect "$IMAGE" >/dev/null

for key in database maintenance local_llm; do
  volume="${PROJECT}_${key}"
  if docker volume inspect "$volume" >/dev/null 2>&1; then
    echo "volume $volume already exists; skipping (delete it manually to restore again)"
    continue
  fi
  echo "== restore $key -> $volume"
  docker volume create \
    --label com.docker.compose.project="$PROJECT" \
    --label com.docker.compose.volume="$key" "$volume" >/dev/null
  if ! docker run --rm --network none --read-only --cap-drop ALL \
    --security-opt no-new-privileges --user 0 \
    --cap-add CHOWN --cap-add DAC_OVERRIDE --cap-add FOWNER \
    --mount "type=bind,src=$PWD/volume_io.py,dst=/volume_io.py,readonly" \
    --mount "type=volume,src=$volume,dst=/volume" \
    --mount "type=bind,src=$PWD/restore,dst=/backup,readonly" \
    --entrypoint python3 "$IMAGE" -B /volume_io.py restore "$key.tar"; then
    # Drop the half-created volume so a rerun restores it instead of skipping it.
    docker volume rm "$volume" >/dev/null || true
    echo "restore of $key failed; volume removed, fix the error and rerun" >&2
    exit 1
  fi
done

echo "complete-volume-restore: next run  python3 connect_cloud.py --cpu"
