#!/usr/bin/env bash
#
# Build Context Builder and publish it to tools.actianlabs.com/contextbuilder.
#
# Manual deployment by design — there is no CI/CD for this project and none is
# wanted. Read it before running it: it restarts a service on a shared host.
#
#   VERSION=0.10.8 ./deploy/deploy.sh
#
# The target is the api VM, NOT cahya. cahya terminates TLS and proxies
# everything through to 10.0.2.101, which is where the tools actually run. The
# nginx vhost on the api VM owns the /contextbuilder locations; deploy.sh does
# not touch nginx.
#
set -euo pipefail

VERSION="${VERSION:?set VERSION to the release you are deploying, e.g. VERSION=0.10.8}"
HOST="${HOST:-api}"
SERVICE="${SERVICE:-contextbuilder}"
APP_PORT="${APP_PORT:-8093}"
PUBLIC_URL="${PUBLIC_URL:-https://tools.actianlabs.com/contextbuilder}"

JAR="snowflake-${VERSION}.jar"
APP_DIR="/opt/contextbuilder"
WEB_DIR="/var/www/tools.actianlabs.com/contextbuilder"
TAG="v${VERSION}"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

say() { printf '\n=== %s\n' "$*"; }
die() { printf '\n!!! %s\n' "$*" >&2; exit 1; }

say "Checking tag ${TAG} exists"
git -C "$REPO_ROOT" rev-parse -q --verify "refs/tags/${TAG}" >/dev/null \
  || die "no tag ${TAG} in this clone — fetch tags, or check the version"

# Build in an isolated worktree of the tag rather than in the checkout.
#
# This repo is worked by several agent sessions at once and the working tree is
# routinely dirty with their in-flight work. `mvn package` in place would bake
# whatever happens to be there into the jar that ships to production, so the
# deployed artifact could match no commit at all. A worktree of the tag is the
# only way to build something that provably corresponds to a release.
BUILD_DIR="$(mktemp -d)/build"
cleanup() {
  git -C "$REPO_ROOT" worktree remove --force "$BUILD_DIR" >/dev/null 2>&1 || true
  rm -rf "$(dirname "$BUILD_DIR")"
}
trap cleanup EXIT

say "Creating a clean worktree of ${TAG}"
git -C "$REPO_ROOT" worktree add -q --detach "$BUILD_DIR" "$TAG" \
  || die "could not create a worktree at ${TAG}"
[ -z "$(git -C "$BUILD_DIR" status --porcelain)" ] \
  || die "the worktree is dirty, which should be impossible — refusing to build"
echo "  $(git -C "$BUILD_DIR" describe --tags --exact-match HEAD)"

say "Running backend tests"
( cd "$BUILD_DIR/backend" && ./mvnw -B test )

say "Building backend"
# `package`, not `verify`: the JaCoCo 60% coverage gate is a pre-push concern,
# not a deploy one, and it must not be able to block a hotfix.
( cd "$BUILD_DIR/backend" && ./mvnw -B clean package -DskipTests )
[ -f "$BUILD_DIR/backend/target/${JAR}" ] \
  || die "expected backend/target/${JAR} — does the pom say ${VERSION}?"

say "Building frontend"
( cd "$BUILD_DIR/frontend" && npm ci && npm run build )
[ -f "$BUILD_DIR/frontend/dist/index.html" ] || die "no frontend/dist/index.html after build"

# The whole deploy is worthless if the bundle is not subpath-aware, and the
# failure mode is a blank page that nothing else explains. Refuse to ship it.
say "Verifying the bundle carries the /contextbuilder prefix"
grep -q '/contextbuilder/assets/' "$BUILD_DIR/frontend/dist/index.html" \
  || die "dist/index.html does not reference /contextbuilder/assets/ — the Vite base is wrong"
grep -q '/contextbuilder/api' "$BUILD_DIR/frontend/dist/assets/"*.js \
  || die "the bundle has no /contextbuilder/api path — API calls would hit the domain root"

say "Uploading backend jar"
scp -q "$BUILD_DIR/backend/target/${JAR}" "${HOST}:${APP_DIR}/${JAR}" || die "jar upload failed"
ssh "$HOST" "set -e
  chmod 644 '${APP_DIR}/${JAR}'
  cp '${APP_DIR}/${JAR}' '${APP_DIR}/contextbuilder.jar'
  echo '  installed as contextbuilder.jar (previous version kept for rollback)'"

say "Uploading static bundle"
# /var/www is www-data-owned, so this stages in /tmp and installs with sudo.
ssh "$HOST" 'rm -rf /tmp/ctx-dist && mkdir -p /tmp/ctx-dist'
rsync -qr "$BUILD_DIR/frontend/dist/" "${HOST}:/tmp/ctx-dist/" || die "bundle transfer failed"
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

say "Smoke: public URL serves"
# Ungated by JGP's ruling (2026-10-16), so there is nothing in front of it — this
# asserts the SPA really serves, and that the bundle on disk is the one just
# built rather than a stale one.
code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 30 "$PUBLIC_URL/")
[ "$code" = "200" ] || die "expected 200 from ${PUBLIC_URL}/, got ${code}"
curl -s --max-time 30 "$PUBLIC_URL/" | grep -q 'Context Builder' \
  || die "the public page did not contain 'Context Builder' — wrong bundle served?"
echo "  200, and the page carries the app title"
# Deliberately no public actuator check: actuator is reachable on the loopback
# port only (checked above) and is never proxied. /contextbuilder/actuator/health
# falls through to the SPA and returns index.html, so probing it would prove
# nothing.

say "Pruning old jars, keeping the two newest"
# JGP's rule (2026-10-16): the current release plus one previous, so rollback
# still has a target. Each jar is ~119 MB and nothing else ever removes them —
# a dozen deploys had reached 1.2 GB before this existed.
#
# Deliberately last: a failed deploy must not delete the jar it would roll back to.
ssh "$HOST" "APP_DIR='${APP_DIR}' bash -s" <<'REMOTE'
set -e
cd "$APP_DIR"
ls -1 snowflake-*.jar 2>/dev/null | sort -Vr | tail -n +3 | while read -r f; do
  printf '  removing %s\n' "$f"
  rm -f -- "$f"
done
printf '  kept:\n'
ls -1 snowflake-*.jar 2>/dev/null | sed 's/^/    /'
REMOTE

say "Done — ${VERSION} is live at ${PUBLIC_URL}/"
