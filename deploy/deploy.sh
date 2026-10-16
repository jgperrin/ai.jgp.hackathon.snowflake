#!/usr/bin/env bash
#
# Build Context Builder locally and publish it to tools.actianlabs.com/contextbuilder.
#
# Manual deployment by design — there is no CI/CD for this project and none is
# wanted. Read it before running it: it restarts a service on a shared host.
#
#   VERSION=0.10.3 ./deploy/deploy.sh
#
# The target is the api VM, NOT cahya. cahya terminates TLS and proxies
# everything through to 10.0.2.101, which is where the tools actually run. The
# nginx vhost on the api VM owns the /contextbuilder locations; deploy.sh does
# not touch nginx.
#
set -euo pipefail

VERSION="${VERSION:?set VERSION to the release you are deploying, e.g. VERSION=0.10.3}"
HOST="${HOST:-api}"
SERVICE="${SERVICE:-contextbuilder}"
APP_PORT="${APP_PORT:-8093}"
PUBLIC_URL="${PUBLIC_URL:-https://tools.actianlabs.com/contextbuilder}"

JAR="snowflake-${VERSION}.jar"
APP_DIR="/opt/contextbuilder"
WEB_DIR="/var/www/tools.actianlabs.com/contextbuilder"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

say() { printf '\n=== %s\n' "$*"; }
die() { printf '\n!!! %s\n' "$*" >&2; exit 1; }

say "Checking tag v${VERSION} exists"
git rev-parse -q --verify "refs/tags/v${VERSION}" >/dev/null \
  || die "no tag v${VERSION} in this clone — fetch tags, or check the version"

say "Running backend tests"
( cd backend && ./mvnw -B test )

say "Building backend"
# `package`, not `verify`: the JaCoCo 60% coverage gate is a pre-push concern,
# not a deploy one, and it must not be able to block a hotfix.
( cd backend && ./mvnw -B clean package -DskipTests )
[ -f "backend/target/${JAR}" ] || die "expected backend/target/${JAR} — does the pom say ${VERSION}?"

say "Building frontend"
( cd frontend && npm ci && npm run build )
[ -f frontend/dist/index.html ] || die "no frontend/dist/index.html after build"

# The whole deploy is worthless if the bundle is not subpath-aware, and the
# failure mode is a blank page that nothing else explains. Refuse to ship it.
say "Verifying the bundle carries the /contextbuilder prefix"
grep -q '/contextbuilder/assets/' frontend/dist/index.html \
  || die "dist/index.html does not reference /contextbuilder/assets/ — the Vite base is wrong"
grep -q '/contextbuilder/api' frontend/dist/assets/*.js \
  || die "the bundle has no /contextbuilder/api path — API calls would hit the domain root"

say "Uploading backend jar"
scp -q "backend/target/${JAR}" "${HOST}:${APP_DIR}/${JAR}" || die "jar upload failed"
ssh "$HOST" "set -e
  chmod 644 '${APP_DIR}/${JAR}'
  cp '${APP_DIR}/${JAR}' '${APP_DIR}/contextbuilder.jar'
  echo '  installed as contextbuilder.jar (previous version kept for rollback)'"

say "Uploading static bundle"
# /var/www is www-data-owned, so this stages in /tmp and installs with sudo.
ssh "$HOST" 'rm -rf /tmp/ctx-dist && mkdir -p /tmp/ctx-dist'
rsync -qr frontend/dist/ "${HOST}:/tmp/ctx-dist/" || die "bundle transfer failed"
ssh "$HOST" "set -e
  sudo rsync -a --delete /tmp/ctx-dist/ '${WEB_DIR}/'
  sudo chown -R www-data:www-data '${WEB_DIR}'
  sudo find '${WEB_DIR}' -type d -exec chmod 755 {} \\;
  sudo find '${WEB_DIR}' -type f -exec chmod 644 {} \\;
  rm -rf /tmp/ctx-dist
  echo '  installed to ${WEB_DIR}'"

say "Restarting ${SERVICE}"
ssh "$HOST" "sudo systemctl restart '${SERVICE}'"
sleep 8
ssh "$HOST" "systemctl is-active '${SERVICE}'" \
  || die "${SERVICE} is not active — check: ssh ${HOST} journalctl -u ${SERVICE} -n 80"

say "Smoke: backend health on the loopback port"
ssh "$HOST" "curl -fsS http://127.0.0.1:${APP_PORT}/actuator/health" && echo

say "Smoke: public URL is gated (401 without credentials)"
code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 30 "$PUBLIC_URL/")
[ "$code" = "401" ] || die "expected 401 from ${PUBLIC_URL}/ without credentials, got ${code}"
echo "  401 as expected"

say "Smoke: serving with credentials"
PW=$(cd ~/GitHub/ops && ./bin/all-creds contextbuilder web password 2>/dev/null) \
  || die "could not read the basic-auth password from all-creds"
curl -fsS -o /dev/null -w '  SPA    HTTP %{http_code}\n' --max-time 30 -u "contextbuilder:${PW}" "$PUBLIC_URL/"
# Deliberately no public actuator check: actuator is reachable on the loopback
# port only (checked above) and is never proxied. /contextbuilder/actuator/health
# falls through to the SPA and returns index.html, so probing it would prove
# nothing.

say "Done. Open ${PUBLIC_URL}/ (basic auth: contextbuilder / see all-creds)."
