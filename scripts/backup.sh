#!/usr/bin/env bash
# Dumps the wallet database to backups/wallet_<timestamp>.sql.gz and keeps the 14 newest dumps.
# Run from anywhere; schedule it daily (cron, or Task Scheduler with Git Bash).
set -euo pipefail

cd "$(dirname "$0")/.."
mkdir -p backups
file="backups/wallet_$(date +%Y%m%d_%H%M%S).sql.gz"

docker compose exec -T wallet-postgres sh -c 'pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB"' | gzip > "$file" \
  || { rm -f "$file"; echo "backup failed" >&2; exit 1; }

# a dump this small means pg_dump printed nothing useful
[ "$(wc -c < "$file")" -gt 200 ] || { rm -f "$file"; echo "backup is empty" >&2; exit 1; }

ls -1t backups/wallet_*.sql.gz | tail -n +15 | xargs -r rm --
echo "$file"
