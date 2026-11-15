#!/usr/bin/env bash
# Automatic deploy from GitHub. Run every 5 minutes by skincare-autodeploy.timer
# (installed by install_autodeploy.sh) from the copy at /srv/skincare/auto_deploy.sh.
#
# 1. git fetch the deploy branch (DEPLOY_BRANCH in /srv/skincare/auto-deploy.env)
# 2. nothing new                    -> exit
#    only mobile/, docs/, *.md     -> record the commit, nothing to rebuild
#    backend/ or admin-web/ changed -> update_server.sh --jar
#    anything else                 -> update_server.sh
# 3. a failed deploy is rolled back to the last good commit and is not retried
#    until a newer commit arrives.
# Status (public, no secrets): https://<public-host>/download/deploy-status.json
#   bash /srv/skincare/auto_deploy.sh            # what the timer runs
#   bash /srv/skincare/auto_deploy.sh --force    # redeploy the branch head (also clears "failed")
set -uo pipefail

BUNDLE="${SKINCARE_BUNDLE:-/srv/skincare}"
REPO="${SKINCARE_REPO:-$HOME/SkinCare-AI}"
STATE="$BUNDLE/auto-deploy"
STATUS_FILE="$BUNDLE/distribution/deploy-status.json"

log() { echo "[auto-deploy] $*"; }

write_status() {  # result commit message
  python3 - "$STATUS_FILE" "$BRANCH" "$(cat "$STATE/deployed" 2>/dev/null)" "$1" "$2" "$3" <<'EOF'
import json, os, subprocess, sys, datetime
path, branch, deployed, result, commit, message = sys.argv[1:]
def subject(c):
    if not c: return ""
    r = subprocess.run(["git", "log", "-1", "--format=%s", c], capture_output=True, text=True)
    return r.stdout.strip()
now = datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=9))).isoformat(timespec="seconds")
old = {}
try:
    old = json.load(open(path))
except Exception:
    pass
data = {
    "branch": branch,
    "deployedCommit": deployed[:12],
    "deployedSubject": subject(deployed),
    "deployedAt": now if result == "success" else old.get("deployedAt"),
    "lastAttempt": {"commit": commit[:12], "subject": subject(commit), "result": result,
                    "message": message, "at": now},
}
tmp = path + ".tmp"
with open(tmp, "w") as f:
    json.dump(data, f, ensure_ascii=False, indent=2)
os.chmod(tmp, 0o644)
os.replace(tmp, path)
EOF
}

deploy_commit() {  # commit jar_flag  — same steps as update_server.sh, for an exact commit
  git -c advice.detachedHead=false checkout --quiet --force --detach "$1" || return 1
  git clean -fdq || return 1
  if [ "$2" = "--jar" ]; then
    log "build admin web"
    docker run --rm -u "$(id -u):$(id -g)" -e HOME=/tmp -e npm_config_cache=/tmp/npm-cache \
      -v "$PWD":/src -w /src/admin-web node:24-bookworm-slim \
      sh -c "npm ci --no-audit --no-fund && npm run build" || return 1
    log "build backend JAR"
    rm -f backend/build/libs/*.jar
    docker run --rm -u "$(id -u):$(id -g)" -e GRADLE_USER_HOME=/src/backend/.gradle-home \
      -v "$PWD":/src -w /src/backend eclipse-temurin:17-jdk \
      sh ./gradlew --no-daemon bootJar -x test -PprebuiltAdminWeb || return 1
  fi
  log "sync code into $BUNDLE"
  python3 deploy/oracle/sync_code.py --bundle "$BUNDLE" $2 || return 1
  log "rebuild changed images and reconnect"
  (cd "$BUNDLE" && sudo python3 connect_cloud.py --cpu)
}

resume_ensure() { sudo systemctl start skincare-ensure.timer 2>/dev/null || true; }

main() {
  [ -f "$BUNDLE/auto-deploy.env" ] && . "$BUNDLE/auto-deploy.env"
  BRANCH="${DEPLOY_BRANCH:-main}"
  mkdir -p "$STATE"
  exec 9>"$STATE/lock"
  flock -n 9 || { log "another deploy is running"; return 0; }

  cd "$REPO" || { log "no git checkout at $REPO (run install_autodeploy.sh)"; return 1; }
  git fetch --quiet origin "$BRANCH" || { log "git fetch failed"; return 1; }
  local target deployed failed
  target="$(git rev-parse FETCH_HEAD)"
  deployed="$(cat "$STATE/deployed" 2>/dev/null || true)"
  failed="$(cat "$STATE/failed" 2>/dev/null || true)"

  if [ "${1:-}" = "--force" ]; then
    rm -f "$STATE/failed"; failed=""; deployed=""
  fi
  [ "$target" = "$deployed" ] && return 0
  if [ "$target" = "$failed" ]; then return 0; fi  # wait for a newer commit

  local jar="--jar" changed relevant
  if [ -n "$deployed" ] && git cat-file -e "$deployed^{commit}" 2>/dev/null; then
    changed="$(git diff --name-only "$deployed" "$target")"
    relevant="$(printf '%s\n' "$changed" | grep -vE '^(mobile/|docs/|\.github/)|\.md$|^\.gitignore$|^$' || true)"
    if [ -z "$relevant" ]; then
      log "${target:0:12}: no server-side changes (app/docs only)"
      echo "$target" > "$STATE/deployed"
      write_status "skipped" "$target" "서버에 반영할 변경 없음(앱·문서만 변경)"
      return 0
    fi
    printf '%s\n' "$relevant" | grep -qE '^(backend/|admin-web/)' || jar=""
  fi

  log "deploying $BRANCH ${target:0:12} (${jar:-no jar}) over ${deployed:0:12}"
  write_status "running" "$target" "반영 중"
  sudo systemctl stop skincare-ensure.timer 2>/dev/null || true
  while systemctl is-active --quiet skincare-ensure.service; do sleep 5; done
  trap resume_ensure EXIT

  if deploy_commit "$target" "$jar"; then
    echo "$target" > "$STATE/deployed"
    rm -f "$STATE/failed"
    log "success ${target:0:12}"
    write_status "success" "$target" "반영 완료"
    return 0
  fi

  echo "$target" > "$STATE/failed"
  log "FAILED ${target:0:12}"
  if [ -n "$deployed" ]; then
    log "rolling back to ${deployed:0:12}"
    if deploy_commit "$deployed" "--jar"; then
      write_status "failed" "$target" "반영 실패 - 이전 버전(${deployed:0:12})으로 되돌림. 서버 로그: journalctl -u skincare-autodeploy"
    else
      write_status "failed" "$target" "반영 실패, 되돌리기도 실패 - 관리자 확인 필요"
    fi
  else
    write_status "failed" "$target" "반영 실패 - 관리자 확인 필요"
  fi
  return 1
}

# Keep everything inside main so bash has parsed the whole file before it runs.
main "$@"; exit $?
