#!/usr/bin/env bash
#
# Point the running service back at a jar already on the box.
#
#   ./deploy/rollback.sh              # list what is available
#   ./deploy/rollback.sh 0.10.2       # switch to that version
#
# Only the backend. The static bundle is not versioned on the host — to revert
# the frontend too, check out the older tag and run deploy.sh for it.
#
set -euo pipefail

HOST="${HOST:-api}"
SERVICE="${SERVICE:-contextbuilder}"
APP_PORT="${APP_PORT:-8093}"
APP_DIR="/opt/contextbuilder"

TARGET="${1:-}"

if [ -z "$TARGET" ]; then
  printf 'Jars on the host:\n'
  ssh "$HOST" "ls -1t ${APP_DIR}/snowflake-*.jar 2>/dev/null || echo '  (none)'"
  printf '\nCurrently running: '
  ssh "$HOST" "readlink -f ${APP_DIR}/contextbuilder.jar 2>/dev/null || echo '${APP_DIR}/contextbuilder.jar (copy, not a symlink)'"
  printf '\nRe-run with a version, e.g. %s 0.10.2\n' "$(basename "$0")"
  exit 0
fi

JAR="snowflake-${TARGET}.jar"

ssh "$HOST" "test -f '${APP_DIR}/${JAR}'" \
  || { printf '!!! %s is not on the host. Run with no argument to list what is.\n' "$JAR" >&2; exit 1; }

printf '=== Switching to %s\n' "$JAR"
ssh "$HOST" "cp '${APP_DIR}/${JAR}' '${APP_DIR}/contextbuilder.jar'"

printf '=== Restarting %s\n' "$SERVICE"
ssh "$HOST" "sudo systemctl restart '${SERVICE}'"
sleep 8

ssh "$HOST" "systemctl is-active '${SERVICE}'" \
  || { printf '!!! %s failed to start — ssh %s journalctl -u %s -n 80\n' "$SERVICE" "$HOST" "$SERVICE" >&2; exit 1; }

ssh "$HOST" "curl -fsS http://127.0.0.1:${APP_PORT}/actuator/health" && echo
printf '\n=== Rolled back to %s.\n' "$TARGET"
