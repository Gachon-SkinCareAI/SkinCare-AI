#!/usr/bin/env bash
# Set up automatic deploys from GitHub on the cloud server (run as the login user, not root).
#   bash deploy/oracle/install_autodeploy.sh main            # install, timer OFF
#   bash deploy/oracle/install_autodeploy.sh main --enable   # install and start the 5-minute timer
# Afterwards: change the branch in /srv/skincare/auto-deploy.env,
#   pause:  sudo systemctl disable --now skincare-autodeploy.timer
#   resume: sudo systemctl enable --now skincare-autodeploy.timer
set -euo pipefail
BRANCH="${1:?usage: install_autodeploy.sh <branch> [--enable]}"
BUNDLE="${SKINCARE_BUNDLE:-/srv/skincare}"
REPO="$HOME/SkinCare-AI"
HERE="$(cd "$(dirname "$0")" && pwd)"
URL="https://github.com/Gachon-SkinCareAI/SkinCare-AI.git"

[ "$(id -u)" -ne 0 ] || { echo "run as the login user (it uses sudo where needed)" >&2; exit 1; }
[ -d "$BUNDLE/private" ] || { echo "$BUNDLE is not a SkinCare bundle" >&2; exit 1; }

if [ ! -d "$REPO/.git" ]; then
  git clone --quiet "$URL" "$REPO"
fi
git -C "$REPO" fetch --quiet origin "$BRANCH"

install -m 755 "$HERE/auto_deploy.sh" "$BUNDLE/auto_deploy.sh"
echo "DEPLOY_BRANCH=$BRANCH" > "$BUNDLE/auto-deploy.env"
mkdir -p "$BUNDLE/auto-deploy"

sed "s/^User=.*/User=$(id -un)/" "$HERE/skincare-autodeploy.service" | sudo tee /etc/systemd/system/skincare-autodeploy.service >/dev/null
sudo cp "$HERE/skincare-autodeploy.timer" /etc/systemd/system/
sudo systemctl daemon-reload
if [ "${2:-}" = "--enable" ]; then
  sudo systemctl enable --now skincare-autodeploy.timer
  echo "complete-autodeploy-enabled ($BRANCH)"
else
  echo "complete-autodeploy-installed ($BRANCH, timer off)"
fi
