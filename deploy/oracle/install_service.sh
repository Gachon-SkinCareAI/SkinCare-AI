#!/usr/bin/env bash
# Install the boot/5-minute health timer. Run once: sudo bash install_service.sh
set -euo pipefail
cd "$(dirname "$0")"
[ "$(id -u)" -eq 0 ] || { echo "run with sudo" >&2; exit 1; }
[ "$PWD" = /srv/skincare ] || echo "warning: service expects /srv/skincare (current: $PWD)"
install -m 0644 skincare-ensure.service /etc/systemd/system/skincare-ensure.service
install -m 0644 skincare-ensure.timer /etc/systemd/system/skincare-ensure.timer
systemctl daemon-reload
systemctl enable --now skincare-ensure.timer
systemctl list-timers skincare-ensure.timer --no-pager
echo "complete-service-install: logs with  journalctl -u skincare-ensure -n 50"
