#!/usr/bin/env bash
# Deploy the latest pushed code to the running Oracle server.
# Run inside the git checkout on the server (e.g. ~/SkinCare-AI):
#   bash deploy/oracle/update_server.sh          # AI / compose / migration changes
#   bash deploy/oracle/update_server.sh --jar    # also rebuild the Spring backend + admin page
# Data, secrets, models and the installed app are untouched.
set -euo pipefail
cd "$(dirname "$0")/../.."
BUNDLE="${SKINCARE_BUNDLE:-/srv/skincare}"
JAR_FLAG=""

echo "== git pull"
git pull --ff-only

if [ "${1:-}" = "--jar" ]; then
  echo "== build the React admin console (/admin/) in a Node container"
  docker run --rm -u "$(id -u):$(id -g)" -e HOME=/tmp -e npm_config_cache=/tmp/npm-cache \
    -v "$PWD":/src -w /src/admin-web node:24-bookworm-slim \
    sh -c "npm ci --no-audit --no-fund && npm run build"
  echo "== build backend JAR in a JDK 17 container (tests skipped; run them on the PC)"
  docker run --rm -u "$(id -u):$(id -g)" -e GRADLE_USER_HOME=/src/backend/.gradle-home \
    -v "$PWD":/src -w /src/backend eclipse-temurin:17-jdk \
    ./gradlew --no-daemon bootJar -x test -PprebuiltAdminWeb
  JAR_FLAG="--jar"
fi

echo "== sync code into $BUNDLE"
python3 deploy/oracle/sync_code.py --bundle "$BUNDLE" $JAR_FLAG

echo "== rebuild changed images and reconnect the fixed address"
cd "$BUNDLE"
sudo python3 connect_cloud.py --cpu
echo "complete-server-update"
