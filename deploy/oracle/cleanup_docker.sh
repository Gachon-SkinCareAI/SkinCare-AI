#!/usr/bin/env bash
# Free disk space left behind by deploys. Run daily by skincare-cleanup.timer.
#   - old images replaced by a newer build (dangling)
#   - Docker build cache not used for 2 days
#   - systemd journal above 200 MB
# Never touches running containers, volumes (DB, LLM), base images reused by builds
# (node, eclipse-temurin, python, ...), /srv/skincare or the backups.
# If the disk is still above 85 % afterwards, the whole build cache is dropped.
set -uo pipefail
LOCK="${SKINCARE_BUNDLE:-/srv/skincare}/auto-deploy/lock"

used() { df --output=pcent / | tail -1 | tr -dc '0-9'; }
avail() { df -h --output=avail / | tail -1 | tr -d ' '; }

main() {
  # Wait for a running auto-deploy instead of deleting the cache it is using.
  mkdir -p "$(dirname "$LOCK")"
  exec 9>"$LOCK"
  flock -w 3600 9 || { echo "cleanup-skipped: deploy still running"; return 0; }

  local before; before="$(used)"
  docker image prune -f >/dev/null
  docker builder prune -f --filter until=48h >/dev/null
  sudo journalctl --vacuum-size=200M >/dev/null 2>&1 || true
  if [ "$(used)" -gt 85 ]; then
    echo "disk still above 85 %: dropping the whole build cache"
    docker builder prune -af >/dev/null
  fi
  echo "cleanup-ok disk ${before}% -> $(used)% (free $(avail))"
}

main "$@"; exit $?
