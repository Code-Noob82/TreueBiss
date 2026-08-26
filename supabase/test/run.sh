#!/usr/bin/env bash
# Prueft Schema und Stempelvergabe gegen ein temporaeres Postgres.
# Braucht postgresql@16 (brew install postgresql@16).
#
# Zwei Szenarien:
#   1. Frische Datenbank  - so sieht ein neues Supabase-Projekt aus.
#   2. Upgrade vom Alt-Stand - Projekt, das vor der Mandantenfaehigkeit
#      eingerichtet wurde. Genau hier scheiterte schema.sql zuerst.
set -euo pipefail

PGBIN="${PGBIN:-/opt/homebrew/opt/postgresql@16/bin}"

# Ohne gesetzte Locale bricht der Postmaster auf macOS beim Start ab
# ("multithreaded during startup").
export LC_ALL="${LC_ALL:-en_US.UTF-8}"
export LANG="${LANG:-en_US.UTF-8}"

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
T="$ROOT/supabase/test"
PORT_BASE="${PGPORT_BASE:-55450}"
FAILED=0

run_scenario() {
    local label="$1" legacy="$2" port="$3"
    local tmp; tmp="$(mktemp -d)"

    "$PGBIN/initdb" -D "$tmp/data" -U postgres --no-sync >/dev/null 2>&1
    "$PGBIN/pg_ctl" -D "$tmp/data" -l "$tmp/pg.log" \
        -o "-p $port -k $tmp -c listen_addresses=''" -w start >/dev/null \
        || { echo "Postgres startete nicht:"; cat "$tmp/pg.log"; return 1; }

    export PGHOST="$tmp" PGPORT="$port" PGUSER="postgres" PGDATABASE="postgres"
    local psql=("$PGBIN/psql" -v ON_ERROR_STOP=1 -q --no-psqlrc)

    echo "=== $label ==="
    "${psql[@]}" -f "$T/00_supabase_stubs.sql" >/dev/null

    if [ "$legacy" = "yes" ]; then
        "${psql[@]}" -f "$T/fixtures_legacy_schema.sql" >/dev/null
        # Ein Stempel aus der Zeit vor der Mandantenfaehigkeit.
        "${psql[@]}" -c "insert into auth.users default values;" >/dev/null
        "${psql[@]}" -c "insert into public.stamps (id, user_id)
                         select gen_random_uuid(), id from auth.users;" >/dev/null
    fi

    # Zweimal einspielen: Das Skript muss wiederholbar sein. NOTICEs ueber
    # uebersprungene Objekte sind dabei erwartet und werden nur bei einem
    # Fehler ausgegeben.
    local i
    for i in 1 2; do
        if ! "${psql[@]}" -f "$ROOT/supabase/schema.sql" >"$tmp/schema.log" 2>&1; then
            echo "Schema-Lauf $i fehlgeschlagen:"
            grep -E "ERROR" "$tmp/schema.log" | head -5
            return 1
        fi
    done
    echo "Schema eingespielt (zweimal, ohne Fehler)."

    local files=()
    if [ "$legacy" = "yes" ]; then
        files=("$T/03_upgrade_test.sql")
    else
        files=("$T/01_issue_stamp_test.sql" "$T/02_rls_test.sql")
    fi

    local f
    for f in "${files[@]}"; do
        if ! "${psql[@]}" -f "$f" 2>&1 \
             | grep -E "(NOTICE|ERROR):" | sed -E 's/^.*(NOTICE|ERROR):  //'; then
            FAILED=1
        fi
    done

    "$PGBIN/pg_ctl" -D "$tmp/data" -m immediate stop >/dev/null 2>&1 || true
    rm -rf "$tmp"
}

run_scenario "Frische Datenbank"      no  "$PORT_BASE"          || FAILED=1
run_scenario "Upgrade vom Alt-Stand"  yes "$((PORT_BASE + 1))"  || FAILED=1

if [ "$FAILED" -ne 0 ]; then
    echo "FEHLGESCHLAGEN"
    exit 1
fi
echo "Alle Szenarien bestanden."
