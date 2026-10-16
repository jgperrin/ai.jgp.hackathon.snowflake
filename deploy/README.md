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
VERSION=0.10.3 ./deploy/deploy.sh
```

It runs the backend tests, builds both artifacts, refuses to continue if the
bundle is missing the `/contextbuilder` prefix, uploads, restarts, and smoke
tests — asserting the public URL answers **200** and carries the app title, so a
stale bundle cannot ship quietly. There is no credential to assert: the tool is
open (see Access above).

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

## Outstanding

1. **The vhost is out of sync with its mirrors.** `tools.actianlabs.com.api.conf`
   is kept **byte-identical in three places** — the ops repo
   (`infra/api-vm/nginx/sites-available/`) and `deploy/nginx/` in both
   `ai.jgp.crm` and `ai.jgp.tools.similardataproduct`. Context Builder is a
   fourth tenant added to the live file and to this repo's copy only; the other
   three still need the same edit.
2. **Snowflake credentials.** Deployed with placeholders, so the tool runs and
   is reachable but every data call returns 502.
3. Minor: the driver logs ~24 lines of EC2-IMDS retry noise at each boot
   (`RetryExec`), which the existing `RestRequest: OFF` in `application.yml`
   does not cover. Bounded and stops after startup, but a one-line fix.
