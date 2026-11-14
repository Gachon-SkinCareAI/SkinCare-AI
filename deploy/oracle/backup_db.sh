#!/usr/bin/env bash
# Dump the app database from the running MySQL container to a gzip file.
#   bash backup_db.sh            # writes /srv/skincare/backups/db/skincare_dev-YYYYmmdd-HHMMSS.sql.gz
# Keeps the newest $KEEP_DAYS days of dumps (default 14). Installed as a daily
# systemd timer by install_backup.sh. The dump contains personal data: never commit it.
set -euo pipefail
BUNDLE="${SKINCARE_BUNDLE:-/srv/skincare}"
CONTAINER="${DB_CONTAINER:-skincare-public-demo-db-1}"
DATABASE="${DB_NAME:-skincare_dev}"
DEST="${BACKUP_DIR:-$BUNDLE/backups/db}"
KEEP_DAYS="${KEEP_DAYS:-14}"

mkdir -p "$DEST"
chmod 700 "$DEST"
stamp="$(TZ=Asia/Seoul date +%Y%m%d-%H%M%S)"
out="$DEST/$DATABASE-$stamp.sql.gz"
tmp="$out.partial"
trap 'rm -f "$tmp"' EXIT

# Read the root password inside the container so it never appears in `ps` on the host.
docker exec "$CONTAINER" sh -c \
  'exec mysqldump -uroot -p"$(cat "$MYSQL_ROOT_PASSWORD_FILE")" \
     --single-transaction --quick --routines --triggers --events \
     --set-gtid-purged=OFF --databases "$0"' "$DATABASE" 2>/dev/null \
  | gzip -9 > "$tmp"

# A finished mysqldump ends with "-- Dump completed"; refuse to keep a truncated file.
if ! gzip -dc "$tmp" | tail -n 1 | grep -q '^-- Dump completed'; then
  echo "backup-failed: dump incomplete" >&2
  exit 1
fi
mv "$tmp" "$out"
chmod 600 "$out"
trap - EXIT

find "$DEST" -name "$DATABASE-*.sql.gz" -mtime +"$KEEP_DAYS" -delete
echo "backup-ok $out $(du -h "$out" | cut -f1)"
