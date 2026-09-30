#!/usr/bin/env bash
# db-logins.sh: make sure the database logins mar_app (read/write) and mar_readonly (read-only) exist in the Oracle
# container's pluggable database FREEPDB1, with the passwords from the local password file.
#
#   scripts/db-logins.sh [container]        container defaults to mar-oracle
#
# The password file, ~/.config/mar/oracle.env (override with MAR_ENV_FILE), lives outside every git repository, has
# mode 600 and holds MAR_DB_PASSWORD (mar_app) and MAR_RO_PASSWORD (mar_readonly). It is created with random passwords
# on first use and reused after. This is the one deliberate place a password is on disk: never in a repository, never
# printed, never on a command line.
#
# A login that is missing (a rebuilt database has none) is created with db/mar-roles.sql; one that exists gets the
# file's password again (ALTER USER ... ACCOUNT UNLOCK). The passwords reach SQL*Plus on its standard input only, and
# its output is filtered so that no statement text (which could hold a password) is ever shown.
set -euo pipefail

CONTAINER=${1:-mar-oracle}
ENV_FILE=${MAR_ENV_FILE:-$HOME/.config/mar/oracle.env}
HERE=$(cd "$(dirname "$0")/.." && pwd)
ROLES_SQL=$HERE/db/mar-roles.sql

die() { echo "db-logins: $*" >&2; exit 1; }

new_password() {  # 24 letters and digits: no quote, ampersand or other character SQL*Plus would treat specially
  local pw=''
  while [ ${#pw} -lt 24 ]; do
    pw+=$(head -c 64 /dev/urandom | tr -dc 'A-Za-z0-9')
  done
  printf '%s' "${pw:0:24}"
}

# --- 1. The password file: create it, or complete it, and keep it private.
umask 077
mkdir -p "$(dirname "$ENV_FILE")"
chmod 700 "$(dirname "$ENV_FILE")"
[ -e "$ENV_FILE" ] || : > "$ENV_FILE"
chmod 600 "$ENV_FILE"
for key in MAR_DB_PASSWORD MAR_RO_PASSWORD; do
  if ! grep -q "^$key=." "$ENV_FILE"; then
    printf '%s=%s\n' "$key" "$(new_password)" >> "$ENV_FILE"
    echo "db-logins: generated $key in $ENV_FILE"
  fi
done

# Read the file as data (KEY=VALUE lines), never execute it.
APP_PW='' RO_PW=''
while IFS='=' read -r key value; do
  case $key in
    MAR_DB_PASSWORD) APP_PW=$value ;;
    MAR_RO_PASSWORD) RO_PW=$value ;;
  esac
done < "$ENV_FILE"
for pw in "$APP_PW" "$RO_PW"; do
  case $pw in *'"'*) die "a password in $ENV_FILE contains a double quote; SQL*Plus cannot take it" ;; esac
done

# --- 2. The container.
[ "$(docker inspect -f '{{.State.Running}}' "$CONTAINER" 2>/dev/null)" = true ] ||
  die "container $CONTAINER is not running (claude_modernization's import-oracle tool builds mar-oracle)"

sql() {  # run SQL (standard input) as SYS in FREEPDB1; prints SQL*Plus's output
  { printf 'SET VERIFY OFF FEEDBACK ON HEADING OFF PAGESIZE 0\nWHENEVER SQLERROR EXIT FAILURE\n'
    printf 'ALTER SESSION SET CONTAINER = FREEPDB1;\n'
    cat; } | docker exec -i "$CONTAINER" sqlplus -S -L / as sysdba
}

existing=$(printf "SELECT 'LOGIN ' || username FROM dba_users WHERE username IN ('MAR_APP', 'MAR_READONLY');\nEXIT\n" |
  sql | sed -n 's/^LOGIN //p') || die "could not query $CONTAINER's FREEPDB1 (is the database open?)"
has() { grep -qx "$1" <<< "$existing"; }

# --- 3. One SQL*Plus session: create what is missing (the matching lines of mar-roles.sql), re-set what exists.
script=$(
  printf 'DEFINE app_pw = "%s"\nDEFINE ro_pw = "%s"\n' "$APP_PW" "$RO_PW"
  skip=()
  has MAR_APP && skip+=(-e mar_app)
  has MAR_READONLY && skip+=(-e mar_readonly)
  if ! { has MAR_APP && has MAR_READONLY; }; then
    # mar-roles.sql without its EXIT and without the lines of logins that already exist
    grep -v -i -w -e '^EXIT' ${skip[@]+"${skip[@]}"} "$ROLES_SQL"
  fi
  has MAR_APP && printf 'ALTER USER mar_app IDENTIFIED BY "&app_pw" ACCOUNT UNLOCK;\n'
  has MAR_READONLY && printf 'ALTER USER mar_readonly IDENTIFIED BY "&ro_pw" ACCOUNT UNLOCK;\n'
  printf 'EXIT\n'
)
unset APP_PW RO_PW

set +e
out=$(printf '%s\n' "$script" | sql 2>&1); rc=$?   # printf is a builtin: no password in the process list
set -e
unset script
created=$(grep -c '^User created' <<< "$out" || true)
altered=$(grep -c '^User altered' <<< "$out" || true)
# Only error codes and messages are shown, never a statement line.
errors=$(grep -E '^(ORA|SP2)-[0-9]+' <<< "$out" || true)
if [ $rc -ne 0 ] || [ -n "$errors" ]; then
  echo "$errors" >&2
  die "SQL*Plus failed in $CONTAINER (exit $rc)"
fi
echo "db-logins: $CONTAINER FREEPDB1: $created login(s) created, $altered password(s) set from $ENV_FILE"
