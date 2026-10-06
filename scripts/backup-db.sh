#!/usr/bin/env bash
set -euo pipefail
: "${PGHOST:=127.0.0.1}" "${PGPORT:=5432}" "${PGDATABASE:=severe_equipment_assets}"
: "${PGUSER:?Set PGUSER}" "${BACKUP_DIR:?Set BACKUP_DIR}" "${ATTACHMENT_DIR:?Set ATTACHMENT_DIR}"
[[ "$PGDATABASE" == severe_equipment_assets ]] || { echo 'Unexpected database' >&2; exit 1; }
command -v pg_dump >/dev/null
mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"
backup_stamp="$(date -u +%Y%m%dT%H%M%SZ)"
export PGHOST PGPORT PGDATABASE PGUSER
# Credentials: ~/.pgpass (0600) or PGPASSFILE; never print secrets.
pg_dump --format=custom --no-owner --file="$BACKUP_DIR/database-$backup_stamp.dump"
# For a consistent DB+file snapshot pause writes or use filesystem snapshots before running.
tar -czf "$BACKUP_DIR/attachments-$backup_stamp.tar.gz" -C "$ATTACHMENT_DIR" .
chmod 600 "$BACKUP_DIR/database-$backup_stamp.dump" "$BACKUP_DIR/attachments-$backup_stamp.tar.gz"
echo "Backup created: $backup_stamp"
