#!/usr/bin/env bash
# run-api.sh: start the web API against the migrated Oracle database, with no password to type.
#
#   scripts/run-api.sh          run in the foreground (./gradlew bootRun) until Ctrl+C
#   scripts/run-api.sh start    build the jar, start it in the background, wait until it answers; log in build/api.log
#   scripts/run-api.sh stop     stop the background API
#   scripts/run-api.sh status   is the background API running?
#
# Settings (environment): MAR_DB_CONTAINER (mar-oracle), MAR_DB_PORT (1522, the mar-oracle-proxy route),
# MAR_API_PORT (8080), MAR_ENV_FILE (~/.config/mar/oracle.env). Before starting, it makes sure the database logins have
# the password file's passwords (scripts/db-logins.sh) and, for the default route, that mar-oracle is on the Docker
# network mar-net and mar-oracle-proxy publishes it on 127.0.0.1:1522 (a rebuilt container is not on mar-net).
set -euo pipefail

HERE=$(cd "$(dirname "$0")/.." && pwd)
CONTAINER=${MAR_DB_CONTAINER:-mar-oracle}
ENV_FILE=${MAR_ENV_FILE:-$HOME/.config/mar/oracle.env}
export MAR_DB_PORT=${MAR_DB_PORT:-1522}
API_PORT=${MAR_API_PORT:-8080}
JAR=$HERE/build/libs/model-oracle-0.0.1-SNAPSHOT.jar
LOG=$HERE/build/api.log
PID_FILE=$HERE/build/api.pid
NET=mar-net PROXY=mar-oracle-proxy

die() { echo "run-api: $*" >&2; exit 1; }
running_pid() { [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null && cat "$PID_FILE"; }

ensure_route() {  # only the default route: localhost:1522 -> mar-oracle-proxy -> mar-net -> mar-oracle:1521
  [ "${MAR_DB_HOST:-localhost}" = localhost ] && [ "$MAR_DB_PORT" = 1522 ] && [ "$CONTAINER" = mar-oracle ] || return 0
  docker network inspect "$NET" > /dev/null 2>&1 || { docker network create "$NET" > /dev/null; echo "run-api: created network $NET"; }
  if ! docker network inspect "$NET" -f '{{range .Containers}}{{.Name}} {{end}}' | grep -qw "$CONTAINER"; then
    docker network connect "$NET" "$CONTAINER"; echo "run-api: connected $CONTAINER to $NET"
  fi
  case $(docker inspect -f '{{.State.Running}}' "$PROXY" 2>/dev/null || echo missing) in
    true) ;;
    false) docker start "$PROXY" > /dev/null; echo "run-api: started $PROXY" ;;
    *) docker run -d --name "$PROXY" --network "$NET" -p 127.0.0.1:1522:1521 \
         alpine/socat tcp-listen:1521,fork,reuseaddr tcp-connect:$CONTAINER:1521 > /dev/null
       echo "run-api: started $PROXY on 127.0.0.1:1522" ;;
  esac
}

prepare() {  # logins, route, and MAR_DB_PASSWORD in this process's environment (never on a command line)
  "$HERE/scripts/db-logins.sh" "$CONTAINER"
  ensure_route
  MAR_DB_PASSWORD=$(sed -n 's/^MAR_DB_PASSWORD=//p' "$ENV_FILE")
  [ -n "$MAR_DB_PASSWORD" ] || die "no MAR_DB_PASSWORD in $ENV_FILE"
  export MAR_DB_PASSWORD
}

case ${1:-run} in
  run)
    [ -z "$(running_pid)" ] || die "the background API is running (pid $(running_pid)); scripts/run-api.sh stop"
    prepare
    cd "$HERE" && exec ./gradlew bootRun
    ;;
  start)
    [ -z "$(running_pid)" ] || die "already running (pid $(running_pid))"
    prepare
    (cd "$HERE" && ./gradlew -q bootJar)
    nohup java -jar "$JAR" > "$LOG" 2>&1 &
    echo $! > "$PID_FILE"
    for _ in $(seq 120); do
      if curl -sf "http://127.0.0.1:$API_PORT/v3/api-docs" > /dev/null; then
        echo "run-api: API running (pid $(cat "$PID_FILE")), http://127.0.0.1:$API_PORT/swagger-ui.html, log $LOG"
        exit 0
      fi
      kill -0 "$(cat "$PID_FILE")" 2>/dev/null || { rm -f "$PID_FILE"; tail -n 30 "$LOG" >&2; die "the API stopped during start-up (log $LOG)"; }
      sleep 1
    done
    die "no answer on port $API_PORT after 120 s (still starting? log $LOG; scripts/run-api.sh stop)"
    ;;
  stop)
    pid=$(running_pid) || { rm -f "$PID_FILE"; echo "run-api: not running"; exit 0; }
    kill "$pid"
    for _ in $(seq 30); do kill -0 "$pid" 2>/dev/null || break; sleep 1; done
    kill -0 "$pid" 2>/dev/null && kill -9 "$pid"
    rm -f "$PID_FILE"
    echo "run-api: stopped (pid $pid)"
    ;;
  status)
    pid=$(running_pid) && echo "run-api: running (pid $pid), log $LOG" || echo "run-api: not running"
    ;;
  *) die "usage: scripts/run-api.sh [run|start|stop|status]" ;;
esac
