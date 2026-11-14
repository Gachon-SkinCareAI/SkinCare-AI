#!/usr/bin/env bash
# One-time host setup for the cloud VM (Google Cloud e2 x86_64 or Oracle Ampere A1 arm64; Ubuntu 22.04/24.04).
# Usage: sudo bash setup_server.sh
# Installs Docker Engine + Compose plugin from Docker's apt repository, adds a 4 GB
# swap file and lets the login user run docker. No inbound port is opened: the
# app reaches the internet through an outbound Cloudflare tunnel only.
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then echo "run with sudo" >&2; exit 1; fi
TARGET_USER="${SUDO_USER:-ubuntu}"
ARCH="$(dpkg --print-architecture)"
case "$ARCH" in amd64|arm64) ;; *) echo "warning: untested architecture $ARCH" ;; esac

echo "== packages"
apt-get update
apt-get install -y ca-certificates curl python3 git
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
. /etc/os-release
echo "deb [arch=$ARCH signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${UBUNTU_CODENAME:-$VERSION_CODENAME} stable" \
  > /etc/apt/sources.list.d/docker.list
apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable --now docker
usermod -aG docker "$TARGET_USER"

echo "== swap (protects against OOM while building images)"
if ! swapon --show | grep -q /swapfile; then
  fallocate -l 4G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

echo "== container user used by backend/ai (UID 10001)"
# Nothing to create: restore_volumes.sh uses the numeric owner (chown 10001:10001),
# which works without a host account. (Creating one named "skincare" also clashes
# when the login user already has that name, e.g. on Google Cloud.)

docker version --format 'docker {{.Server.Version}} ({{.Server.Arch}})'
docker compose version
echo "complete-server-setup: log out and back in so '$TARGET_USER' can use docker without sudo"
