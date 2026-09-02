#!/usr/bin/env bash
# Spielt einen erfundenen Betrieb gegen ein frisches Postgres durch.
set -euo pipefail
PGBIN="${PGBIN:-/opt/homebrew/opt/postgresql@16/bin}"
export LC_ALL="${LC_ALL:-en_US.UTF-8}" LANG="${LANG:-en_US.UTF-8}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
T="$ROOT/supabase/test"
tmp="$(mktemp -d)"; port="${PGPORT_SPIEL:-55480}"
"$PGBIN/initdb" -D "$tmp/data" -U postgres --no-sync >/dev/null 2>&1
"$PGBIN/pg_ctl" -D "$tmp/data" -l "$tmp/pg.log" -o "-p $port -k $tmp -c listen_addresses=''" -w start >/dev/null
export PGHOST="$tmp" PGPORT="$port" PGUSER="postgres" PGDATABASE="postgres"
psql=("$PGBIN/psql" -v ON_ERROR_STOP=1 -q --no-psqlrc)
"${psql[@]}" -f "$T/00_supabase_stubs.sql" >/dev/null
"${psql[@]}" -f "$ROOT/supabase/schema.sql" >/dev/null 2>&1
"${psql[@]}" -f "$T/durchspielen.sql" 2>&1 | sed -E 's/^(NOTICE|INFO):  //'
"$PGBIN/pg_ctl" -D "$tmp/data" -m immediate stop >/dev/null 2>&1 || true
rm -rf "$tmp"
