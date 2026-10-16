# Deploying Context Builder to tools.actianlabs.com/contextbuilder

Manual deployment. No CI/CD for this project, by choice. Every step below is
separately checkable so a failure tells you where it happened.

| | |
|---|---|
| Public URL | `https://tools.actianlabs.com/contextbuilder/` |
| Access | **Open** — no auth, no landing-page card (JGP, 2026-10-16) |
| Runs on | the **api VM**, `10.0.2.101` (`ssh api`, user `jgp`) — *not* cahya |
| Backend | `/opt/contextbuilder/contextbuilder.jar`, systemd unit `contextbuilder`, `127.0.0.1:8093` |
| Frontend | `/var/www/tools.actianlabs.com/contextbuilder/` (www-data) |
| Config | `/opt/contextbuilder/.env`, `jgp:jgp` mode 0600 |

## The deploying rule — read this first

**Standing instruction (JGP, 2026-10-16): keep the deployment on the newest
release. Watch for new tags and deploy them as they land.**

### Deploy the newest tag

```bash
VERSION=<x.y.z> ./deploy/deploy.sh
```

Nothing else is needed. The script refuses to run without `VERSION`, checks the
tag exists, and then does the whole job — see "What the script does for you".

### Watch for new tags

Run this as a background watcher, replacing `LAST` with the version just
deployed. It prints one line per new release, which is the trigger to deploy.

```bash
cd /Users/jean-georges.perrin/GitHub/ai.jgp.hackathon.snowflake
LAST=v0.10.23
while true; do
  sleep 60
  NOW=$(git ls-remote --tags --refs origin 2>/dev/null \
        | sed 's#.*refs/tags/##' | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' \
        | sort -V | tail -1)
  if [ -n "$NOW" ] && [ "$NOW" != "$LAST" ]; then
    echo "NEW RELEASE $NOW — deployed is $LAST, redeploy needed"
    LAST="$NOW"
  fi
done
```

(Use `if`, not an `&&` chain: the chain returns non-zero whenever there is no
new tag, which kills the loop under `set -e`.)

**Re-arm it every 30 minutes.** A long-running watcher is killed at 30 minutes
and a tag landing in the gap is missed silently — that happened once here, hiding
seven releases. Prefer `Monitor` where available; otherwise plan to restart it.

### What the script does for you

- **Builds in a detached worktree of the tag**, never in the checkout. Other
  agent sessions keep this checkout dirty, and an in-place build would bake their
  uncommitted work into the shipped jar. This also means a stale, diverged or
  dirty checkout cannot corrupt a deploy.
- **Refuses a bundle missing the `/contextbuilder` prefix**, since that ships as a
  blank page and nothing else explains why.
- **Uploads the jar as `.part`** and renames it into place only after a sha256
  match, so a failed transfer cannot leave a truncated jar where rollback would
  find it.
- **Prunes to the two newest jars** after the smoke tests pass, so a failed deploy
  cannot delete the jar it would roll back to.
- **Smoke tests** the loopback health endpoint and the public URL.

### Verify afterwards

```bash
curl -s -o /dev/null -w '%{http_code}\n' https://tools.actianlabs.com/contextbuilder/   # 200
for p in / /schema/ /dpcompare/ /crm/; do
  printf '%-12s %s\n' "$p" "$(curl -s -o /dev/null -w '%{http_code}' https://tools.actianlabs.com$p)"
done   # all 200 — this vhost serves four tools and a bad edit breaks the others
```

Then confirm the running jar is byte-identical to the tag:

```bash
ssh api 'md5sum /opt/contextbuilder/contextbuilder.jar /opt/contextbuilder/snowflake-<x.y.z>.jar'
```

### Things that will bite

- **`git pull` on `main` can fail with "not possible to fast-forward".** The
  checkout usually carries another session's unpushed commit. Leave it alone —
  never reset, stash or force. Since deploys build from a tag worktree, the
  checkout's state does not matter.
- **Rollback reaches back exactly one release** (`rollback.sh`), because only two
  jars are kept. Older needs a rebuild from its tag, ~4 minutes.
- **Docs-only tags still get deployed** unless JGP says otherwise. Several
  releases (0.10.5, 0.10.6, 0.10.10, 0.10.11, 0.10.17, 0.10.19) ship nothing in
  the jar; deploying them restarts a live service for a version string. Asked,
  not yet ruled on.
- **Snowflake is live, and the shared profile is seeded.** Since 0.10.7 each
  signed-in user enters their own at `/api/me/connection`. The server's
  `SNOWFLAKE_*` values (set 2026-10-16, hackathon account) pre-fill the demo
  profile so the judges' login arrives **ready** instead of prompting. Verified
  end to end the same day: catalog browse and Cortex both return real data.
  Treat those values as configuration, not placeholders — blanking them takes the
  ready-state away. Judge login is `jgp` / `odcs`.

### Known state, not yet done

- **The vhost is out of sync with its mirrors.** `tools.actianlabs.com.api.conf`
  is kept byte-identical in four places — the ops repo
  (`infra/api-vm/nginx/sites-available/`) and `deploy/nginx/` in this repo,
  `ai.jgp.crm` and `ai.jgp.tools.similardataproduct`. Only this repo's copy and
  the live file know about `/contextbuilder`; the other three still have three
  tenants, so reinstalling from one would silently drop this tool.
- **Self-report is off** (503 `not_configured`) until the server has
  `GITHUB_SELF_REPORT_TOKEN` (issues + contents write) and the repo has a
  `used-for-self-reports-do-not-delete` branch.
- **`/contextbuilder/api/` inherits the vhost's 5 MB body cap**, so larger
  self-report attachment sets get a 413 — it needs its own `client_max_body_size`
  to accept them.
- **The api VM root filesystem is at 93%** (23 GB free). Not this tool (~357 MB),
  but everything on that host shares the consequence.
- **The driver logs ~24 lines of EC2-IMDS retry noise at each boot**
  (`RetryExec`), which the existing `RestRequest: OFF` in `application.yml` does
  not cover. Bounded, stops after startup, but a one-line fix.

## The architecture, because it is two hops and the second one is easy to miss

`tools.actianlabs.com` resolves to **cahya** (`40.160.32.221`) — a Proxmox host
whose *only* job is TLS termination and reverse proxy. App workloads explicitly
do not run there. cahya passes `/` straight through to the **api VM**
(`10.0.2.101`), and that is where every tool lives.

So there are two nginx configs, and only one of them is ours to change:

- `cahya:/etc/nginx/sites-enabled/tools.actianlabs.com.conf` — pure pass-through
  to `10.0.2.101:80`. **Requires no change for a new tool** : it already fronts
  every tenant with `client_max_body_size 64M` and 300s timeouts, which is
  headroom we do not exceed. (Its own comments warn that a tenant with a bigger
  appetite *would* need an edit here — we are not that tenant.)
- `api:/etc/nginx/sites-available/tools.actianlabs.com.api.conf` — the real
  vhost, one path prefix per tool. **This is the file we edit.**

### House pattern for a tool at `/<name>/`

- Static SPA at `/var/www/tools.actianlabs.com/<name>/`, served with `root`
  (never `alias` — `alias` + `try_files` resolves `$uri` against the alias path
  and 404s every asset).
- Backend as a systemd unit running `/opt/<name>/<name>.jar`, `User=jgp`,
  `WorkingDirectory=/opt/<name>`, config in `/opt/<name>/.env` read via
  `EnvironmentFile=-/opt/<name>/.env`.
- nginx **strips the prefix**: `location /<name>/api/ { proxy_pass http://127.0.0.1:<port>/api/; }`.
  The app serves `/api/*` unprefixed and `server.servlet.context-path` stays `/`.
- Backends must set `SERVER_ADDRESS=127.0.0.1`. Spring binds `0.0.0.0`
  otherwise, publishing the port to every peer on `10.0.2.0/24` and bypassing
  nginx, its auth, and its path structure entirely.

**Ports.** Read `~/GitHub/ops/infra/api-vm/README.md` before choosing one — a
collision surfaces as a silent failure to bind. 8080 is Bitol, 8083 bitol-mcp,
8086/8087 next, 8088 controlcenter, 8089 schema-drift-advisor, 8090 dpcompare,
8091/8092 crm. Context Builder took **8093**.

## Routine deploy

```bash
VERSION=0.10.8 ./deploy/deploy.sh
```

It runs the backend tests, builds both artifacts, refuses to continue if the
bundle is missing the `/contextbuilder` prefix, uploads, restarts, and smoke
tests — asserting the public URL answers **200** and carries the app title, so a
stale bundle cannot ship quietly. There is no credential to assert: the tool is
open (see Access above).

**It builds in a worktree of the tag, not in this checkout.** This repo is worked
by several agent sessions at once and the working tree is routinely dirty with
their in-flight work, so `mvn package` in place would bake whatever happened to
be there into the jar that ships — producing a deployed artifact that matches no
commit. The worktree is created detached at the tag, used for tests and both
builds, and removed on exit even if the run fails. Nothing about your current
working tree is touched.

## First-time provisioning (already done, 2026-10-16 — kept for rebuilds)

```bash
# On the api VM
sudo mkdir -p /opt/contextbuilder /var/www/tools.actianlabs.com/contextbuilder
sudo chown jgp:jgp /opt/contextbuilder
sudo chown www-data:www-data /var/www/tools.actianlabs.com/contextbuilder

scp deploy/env.template api:/tmp/ctx.env
ssh api 'sudo install -o jgp -g jgp -m 600 /tmp/ctx.env /opt/contextbuilder/.env && rm /tmp/ctx.env'
# then fill in the real Snowflake values

scp deploy/systemd/contextbuilder.service api:/tmp/
ssh api 'sudo mv /tmp/contextbuilder.service /etc/systemd/system/ \
         && sudo systemctl daemon-reload && sudo systemctl enable --now contextbuilder'

# nginx: install the vhost in deploy/nginx/, then
ssh api 'sudo nginx -t && sudo systemctl reload nginx'
```

### Auth

None, deliberately. Basic auth was added at provisioning on the reasoning that
this tool reaches a live Snowflake catalog while `/schema` and `/dpcompare` are
static comparators; JGP removed it the same day. The vhost carries no
`auth_basic` and `/etc/nginx/.htpasswd-contextbuilder` is gone.

If it is ever reinstated, `htpasswd` is **not** installed on the api VM and does
not need to be — `openssl passwd -apr1` produces a hash nginx accepts. Note the
`.htpasswd` file must be readable by `www-data`.

## Verify

```bash
curl -s https://tools.actianlabs.com/contextbuilder/ | grep /contextbuilder/assets/
ssh api 'curl -s localhost:8093/actuator/health'                  # {"status":"UP"}
```

The asset-path check is the one that matters: if those paths lack the prefix the
page renders blank and nothing else will explain why.

**Regression check after any vhost edit** — this host serves four tools:

```bash
for p in / /schema/ /dpcompare/ /crm/; do
  printf '%-14s ' "$p"; curl -s -o /dev/null -w '%{http_code}\n' "https://tools.actianlabs.com$p"
done
```

## Rollback

```bash
./deploy/rollback.sh              # list jars on the host
./deploy/rollback.sh 0.10.2       # switch back
```

Backend only. The static bundle is not versioned on the host — to revert the
frontend too, check out the older tag and run `deploy.sh`.

**Only the last two jars are kept.** `deploy.sh` prunes after a successful
deploy, leaving the current release plus one previous so `rollback.sh` always has
a target. So rollback reaches back exactly one release; anything older needs a
rebuild from its tag. Jars are ~119 MB each and nothing else removes them — a
dozen deploys had reached 1.2 GB before the prune existed.

For the vhost, timestamped backups sit beside it:

```
/etc/nginx/sites-available/tools.actianlabs.com.api.conf.bak-<YYYYmmdd-HHMMSS>
```

## Troubleshooting

| Symptom | Cause |
|---|---|
| Blank page, 404s on `/assets/...` | Vite `base` not applied — bundle built without it |
| 404s on `/api/...` (no prefix) | `src/api/http.ts` has hardcoded paths, or built from a pre-fix commit |
| **502** on catalog / enrich / Cortex | Backend up, Snowflake not: check `/opt/contextbuilder/.env`. Hikari is set not to validate at boot, so the service starts regardless — by design |
| 502 on *everything* including `/actuator/health` on loopback | Service not listening; `journalctl -u contextbuilder -n 80` |
| `503` after a vhost edit | Reload failed; `ssh api 'sudo nginx -t'` and restore the `.bak` |
| Service restart-loops | Bad `ExecStart` java path, or a malformed `.env` (a `$(...)` or quoted value) |
