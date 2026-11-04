#!/usr/bin/env bash
# Run only on the new demo server. Default mode is read-only.
set -euo pipefail
MODE="${1:---check}"
case "$MODE" in --check|--apply) ;; *) echo 'Usage: bash prepare-ubuntu.sh [--check|--apply]' >&2; exit 2 ;; esac
[[ -r /etc/os-release ]] || { echo 'Linux Ubuntu server required.' >&2; exit 1; }
source /etc/os-release
[[ "$ID" == ubuntu && "$VERSION_ID" == 24.04 && "$(uname -m)" == x86_64 ]] || {
  echo 'This installer targets a new Ubuntu 24.04 x86_64 server.' >&2; exit 1;
}
memory_kib=$(awk '/MemTotal:/ {print $2}' /proc/meminfo)
free_kib=$(df -Pk / | awk 'NR==2 {print $4}')
[[ "$memory_kib" -ge 7340032 ]] || { echo 'At least 7 GiB usable RAM required for this 8 GB plan.' >&2; exit 1; }
[[ "$free_kib" -ge 31457280 ]] || { echo 'At least 30 GiB free root disk space required.' >&2; exit 1; }
printf 'Platform: Ubuntu %s / %s; CPUs: %s; memory KiB: %s; free disk KiB: %s\n' "$VERSION_ID" "$(uname -m)" "$(nproc)" "$memory_kib" "$free_kib"
if [[ "$MODE" == --check ]]; then
  if command -v docker >/dev/null; then
    docker --version
    docker compose version
  else
    echo 'Docker is not installed yet.'
  fi
  echo 'Read-only check complete. No packages, firewall rules or application data changed.'
  exit 0
fi
[[ "$EUID" -eq 0 ]] || { echo 'Use sudo bash prepare-ubuntu.sh --apply on the new server.' >&2; exit 1; }
umask 077
base=/opt/skincare-ai
if [[ -e "$base" && ! -f "$base/.demo-host-v1" ]]; then
  echo 'Existing unmarked /opt/skincare-ai found; refusing to adopt or overwrite it.' >&2
  exit 1
fi
if ! command -v docker >/dev/null; then
  for package in docker.io docker-compose docker-compose-v2 podman-docker containerd runc; do
    if dpkg-query -W -f='${Status}' "$package" 2>/dev/null | grep -q '^install ok installed$'; then
      echo "Existing package $package requires review. Nothing will be removed automatically." >&2
      exit 1
    fi
  done
  [[ ! -e /etc/apt/sources.list.d/docker.sources && ! -e /etc/apt/sources.list.d/docker.list ]] || {
    echo 'Existing Docker repository configuration requires review.' >&2; exit 1;
  }
  export DEBIAN_FRONTEND=noninteractive
  apt-get update
  apt-get install -y ca-certificates curl
  install -m 0755 -d /etc/apt/keyrings
  curl --fail --show-error --silent --location https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod 0644 /etc/apt/keyrings/docker.asc
  cat > /etc/apt/sources.list.d/docker.sources <<'DOCKER_SOURCE'
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: noble
Components: stable
Architectures: amd64
Signed-By: /etc/apt/keyrings/docker.asc
DOCKER_SOURCE
  chmod 0644 /etc/apt/sources.list.d/docker.sources
  apt-get update
  apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi
docker compose version
systemctl enable --now docker
docker info --format 'Docker server={{.ServerVersion}} OS={{.OSType}} architecture={{.Architecture}}'
install -m 0700 -d "$base" "$base/releases"
printf 'skincare-demo-host-v1\n' > "$base/.demo-host-v1"
{
  date -u '+preparedAt=%Y-%m-%dT%H:%M:%SZ'
  docker --version
  docker compose version
} > "$base/host-preparation.txt"
chmod 0600 "$base/.demo-host-v1" "$base/host-preparation.txt"
echo 'Host prepared. App deployment, DNS and public HTTPS verification are still separate steps.'
echo 'Use the Lightsail firewall: TCP 80/443 public; TCP 22 only from your management IP.'
