# Changelog

All notable changes to this project are documented here. Backend and frontend versions move in lockstep.

## [0.10.32] - 2026-10-16

### Added
- About (DIND#82): a new item in the profile menu opens a view with the Context
  Builder poster (`art/github2.jpg`, now bundled) as a full-bleed cover background
  under a scrim. The copy covers what the app does, the five steps, the
  standards it writes, the repository link and the prototype disclaimer. The
  wizard stays mounted underneath, so Back returns to it unchanged.

## [0.10.31] - 2026-10-16

### Fixed
- `deploy/README.md` claimed the Snowflake credentials were placeholders and that
  every data call returns 502 (DIND#83). They are live as of 2026-10-16 — the
  demo profile is seeded and catalog browse and Cortex both return real data.
  Also folds the duplicate `## Outstanding` section into "Known state".

### Note
- No artifact change: documentation only.

## [0.10.30] - 2026-10-16

### Added
- "Saved in Snowflake" (DIND#63): a top-bar view listing every save in
  `<db>.DATA_PRODUCTS.ARTIFACTS`, newest first, versions included. Each save
  expands to its product, contracts and OSI model, shows each artifact's stored
  YAML (Copy / Download), and can be opened into the wizard. The wizard stays
  mounted underneath, so its step and bundle survive a visit. Step 5 links to it.
- `GET /api/products/saves` (read-only, `LIMIT 200` artifact rows) and
  `GET /api/products/{id}/artifacts` (UUID-guarded, bind parameter).

## [0.10.29] - 2026-10-16

### Fixed
- Closing the Snowflake connection panel returns keyboard focus to whatever
  opened it: the profile menu button (the menu item itself is gone by then) or
  the main-page card's button. It no longer drops focus on the page body (DIND#78).

## [0.10.28] - 2026-10-16

### Fixed
- Under `npm run dev:mock` the Help panel answers locally with a fake
  `mock.invalid` issue URL instead of proxying `POST /api/self-report` to
  whatever backend is on :8080, which showed a false "Signed out" (DIND#74).
  The branch is dropped from production builds.

## [0.10.27] - 2026-10-16

### Changed
- Profile menu (DIND#69): the top bar's "Signed in as", connection badge and
  Sign out button fold into one menu on the right (initials, name, status dot).
  It holds the Snowflake connection, with its state shown as Ready, Not
  connected or Needs setup, and Sign out. Full menu keyboard support: arrows,
  Home/End, Escape returns focus, click outside closes.

## [0.10.26] - 2026-10-16

### Changed
- A warm first connection (DIND#67). A profile the server already holds (the
  demo user, seeded from `SNOWFLAKE_*`) now reads as ready: the wizard is usable
  straight after sign-in, the connection panel no longer opens over it, and the
  header says "ready" instead of "not connected". The pool still opens on first
  use.
- The connection view tells three states apart: nothing saved, saved but not
  opened, connected (`state`: `NONE` / `SAVED` / `CONNECTED`; `connected` is
  unchanged).
- The panel opens prefilled: from the saved profile, or, when nothing is saved,
  from the server's settings via the new `GET /api/me/connection/defaults`
  (account, user, warehouse, database, role; blanks when unset; never the
  password). The password field stays blank and write-only. The amber paragraph
  is now one line, and the main-page card describes the real state.

## [0.10.25] - 2026-10-16

### Added
- `deploy/README.md` opens with the deploying rule (DIND#71): the standing
  instruction, the exact command, a tested watcher snippet for new release tags,
  what `deploy.sh` already guarantees, and the gotchas that have actually bitten
  — a diverged checkout, the 30-minute watcher expiry, one-release rollback.
  Known outstanding state is recorded there too.

### Note
- No artifact change: documentation only.

## [0.10.24] - 2026-10-16

### Changed
- The standards pills under the headline credit Bitol: `Bitol ODCS v3.2`,
  `Bitol ODPS v1.1`, and `OSI/Apache Ossie` drops to the muted grey badge, since
  it is derived from the other two rather than a third standard (DIND#68).

## [0.10.23] - 2026-10-16

### Added
- In-app Help (DIND#60): a `?` button in the top bar opens a "Report a problem"
  panel (description, up to 5 attachments, paste a screenshot) that files a
  `[Self-report]` issue in this repo through `POST /api/self-report`, with the
  app version, wizard step, route, viewport, a connected yes/no and the last API
  errors attached. Credentials, connection settings, cookies and the caller IP
  never reach the issue (pinned by tests). Rate-limited to 5 reports per hour
  per IP. With no `GITHUB_SELF_REPORT_TOKEN` the app starts normally and the
  endpoint answers 503 `not_configured`.

### Deploy notes
- Reporting is off until the server has `GITHUB_SELF_REPORT_TOKEN` (issues write
  + contents write) and the repo has the `used-for-self-reports-do-not-delete`
  branch. nginx still caps `/contextbuilder/api/` at 5 MB, so larger attachment
  sets get a 413 until that is raised.

## [0.10.22] - 2026-10-16

### Added
- Appearance switch in the top bar (DIND#59): System (default) / Light / Dark as
  three icon buttons, stored per browser as `cb-appearance`. An explicit choice
  overrides the OS, `color-scheme` follows it, and an inline script in
  `index.html` applies it before first paint, so there's no flash of the wrong theme.
  The classification/tag chip colours moved into `--tag-bg` / `--tag-text`
  tokens.

## [0.10.21] - 2026-10-16

### Changed
- Source step: the database is picked from a filterable list instead of a
  dropdown, and Snowflake's own databases (`SNOWFLAKE`, `SNOWFLAKE_SAMPLE_DATA`,
  `ORGDATACLOUD$*`, `USERS$*`) are hidden behind a "Show system databases (n
  hidden)" toggle (DIND#62). On the lab account that cuts 22 rows to the five
  account databases. `GET /api/catalog/databases` now carries `system` on every
  row; the name rule lives in `SystemDatabases`.

## [0.10.20] - 2026-10-16

### Changed
- One brand mark everywhere (DIND#61): an original stellar-plate snow crystal
  (six-fold, hexagonal plate, a spoke and a node per vertex) replaces the
  stacked-layers glyph in the header, the connect callout and the sign-in page,
  and the favicon now draws the same geometry in palette indigo (indigo-300 in a
  dark tab bar). The old favicon's off-palette purple, gradients and 17 blur
  filters are gone. Comparison: `art/brand-mark-61.png`.

## [0.10.19] - 2026-10-16

### Fixed
- `deploy.sh` uploads the jar to a `.part` name and renames it into place only
  after a sha256 check (DIND#56). A transient `scp` failure while shipping
  v0.10.18 left a **zero-byte** `snowflake-0.10.18.jar` in the store, where the
  prune counted it as a release and `rollback.sh 0.10.18` would have copied that
  empty file over the running jar.

### Note
- No artifact change: ops files only.

## [0.10.18] - 2026-10-16

### Fixed
- Users stay signed in across backend restarts and deploys: sign-in also sets a signed cookie (HMAC-SHA256, 12 h, HttpOnly, SameSite=Lax) that rebuilds the lost session. Key from `CONTEXTBUILDER_AUTH_SECRET`, else derived from the demo credentials. Mid-analysis "Sign in first" after a restart is gone (DIND#52).

## [0.10.17] - 2026-10-16

### Added
- `deploy.sh` prunes old jars after a successful deploy, keeping the two newest
  (DIND#53). Nothing removed them before, so a dozen deploys had left 10 jars and
  1.2 GB on the api VM; the store is now 357 MB. Deliberately runs last, so a
  failed deploy cannot delete the jar it would roll back to.

### Note
- No artifact change: ops files only.

## [0.10.16] - 2026-10-16

### Changed
- Analyze step and README: data-quality rules and SLAs are "collectively named Data QoS", linked to the Data QoS article (DIND#50).

## [0.10.15] - 2026-10-16

### Fixed
- Analyze starts a background job and polls it every 2 s instead of holding one long request, so a proxy timeout can no longer end it with "502 Bad Gateway"; up to 5 failed polls in a row are retried (DIND#47).
- A bare 502/503/504 from a proxy now reads "The server is unreachable or restarting. Try again in a moment."

## [0.10.14] - 2026-10-16

### Fixed
- Analyze no longer depends on one long HTTP request: `POST /api/enrich/jobs` starts the Cortex analysis in the background (as the signed-in user) and `GET /api/enrich/jobs/{id}` reports RUNNING / DONE / FAILED with the result. Behind a proxy with a short read timeout the old single request ended in a bare 502 (DIND#46).
- A body that is not a bundle now answers 400 instead of 500 on enrich, quality run and OSI export.

## [0.10.13] - 2026-10-16

### Changed
- Frontend restyled to the jgp.ai / Actian Labs house style (ops/ux): Roboto, the Actian-blue and Cool Blue-Gray palette, 56px white top bar with brand mark and version pill, PageHeader with the ODCS / ODPS / OSI chips, card / button / badge / table / chip components, the hourglass loading ring, and the canonical Actian Labs footer (collapsible, `DO NOT USE FOR PRODUCTION`, `Use at your own risk.`). Sign-in is a centered card with the judges' demo-account callout. Light and dark follow the system appearance. Behaviour and API calls unchanged (DIND#36).

## [0.10.12] - 2026-10-16

### Fixed
- Generate no longer logs deprecated container `@Valid` warnings (DIND#43).

## [0.10.11] - 2026-10-16

### Changed
- README rewritten for Context Builder: pitch, try-it (jgp / odcs), live results on the lab data, mapping, architecture, run, API (DIND#41).

### Added
- `doc/DEMO.md`: 3-minute demo script with timings.

## [0.10.10] - 2026-10-16

### Changed
- `deploy/deploy.sh` builds in a detached **worktree of the tag** instead of the
  shared checkout (DIND#38). The checkout is routinely dirty with other agent
  sessions' in-flight work, so an in-place build would bake uncommitted code into
  the jar that ships — a deployed artifact matching no commit. The worktree is
  removed on exit, including on failure.

### Note
- No artifact change: ops files only.

## [0.10.9] - 2026-10-16

### Added
- Analysis proposes a primary key for each table that declares none: the first identifier-named column with no nulls and one distinct value per row examined (deterministic, from the profile). Accepting it marks the property `primaryKey`/`required`, which gives the OSI/Apache Ossie export its `primary_key` and inferred relationships (DIND#37).

### Fixed
- Blank `SNOWFLAKE_*` settings no longer seed the demo profile with empty strings (e.g. `role: ""`).

## [0.10.8] - 2026-10-16

### Changed
- Headline now reads "From Snowflake schema to AI-ready data product" (wizard and sign-in) (DIND#34).

### Added
- Backend sign-in (`/api/auth/*`, demo account `jgp` / `odcs` overridable by `CONTEXTBUILDER_AUTH_DEMO_USER` / `CONTEXTBUILDER_AUTH_DEMO_PASSWORD`) and a per-user Snowflake connection profile (`/api/me/connection`) with one pool per user; the app now boots with no Snowflake settings, and `SNOWFLAKE_*` only pre-fills the demo user's profile. Shipped inside v0.10.5 without its own entry (DIND#27).

## [0.10.7] - 2026-10-16

### Added
- Sign-in page with the demo credentials from `GET /api/auth/demo` shown for the judges (DIND#28).
- Header with the signed-in user, a **Snowflake connection** button and **Sign out** (DIND#28).
- Snowflake connection panel: account, user, write-only password, warehouse, role and database
  (where products are saved); **Test & save** shows "Connected ✓" or Snowflake's error (DIND#28).
- Global handling: any 401 returns to sign-in; a 409 `NO_CONNECTION` opens the connection panel (DIND#28).
- Dev mock (`npm run dev:mock`) covers sign-in (`jgp` / `odcs`) and the connection endpoints (DIND#28).

## [0.10.6] - 2026-10-16

### Removed
- Basic auth on tools.actianlabs.com/contextbuilder (DIND#31). It was added at
  provisioning because this tool reaches a live Snowflake catalog while
  `/schema` and `/dpcompare` are static comparators; JGP removed it the same day.

### Changed
- `deploy/deploy.sh` now asserts the public URL answers **200** and carries the
  app title, instead of asserting a 401 without credentials.

### Note
- No artifact change: documentation and ops files only.

## [0.10.5] - 2026-10-16

### Added
- `deploy/` — the runbook and artifacts for getting this tool onto
  tools.actianlabs.com/contextbuilder: the live nginx vhost, the systemd unit, an
  env template, and `deploy.sh` / `rollback.sh`. Replaces an earlier attempt that
  documented the wrong host (DIND#29).

### Note
- No artifact change: this release ships documentation and ops files only. The jar
  and the bundle are functionally identical to 0.10.4.

## [0.10.4] - 2026-10-16

### Changed
- The app is now called **Context Builder** (tab title and header) (DIND#25).

### Added
- Project art under `art/`; README opens with the Context Builder banner.

## [0.10.3] - 2026-10-16

### Fixed
- Validator log level moved under `logging.level`; it had landed under `management.health` and had no effect (DIND#23).

## [0.10.2] - 2026-10-16

### Fixed
- Cortex answers were truncated mid-JSON by the default output limit, losing whole tables: `AI_COMPLETE` now runs with `max_tokens` 8192 and temperature 0, and the prompt asks for shorter text (DIND#21).
- Lenient JSON parsing (trailing commas, comments, single quotes); one retry on an unparseable answer, with its length and tail logged.
- Both `AI_COMPLETE` return shapes are handled (plain text or a `choices` envelope).
- Validator "Unknown keyword" startup warnings muted.

## [0.10.1] - 2026-10-16

### Fixed
- Live queries failed on Java 21 because the Snowflake driver's Arrow result decoding needs `--add-opens`; connections now use `JDBC_QUERY_RESULT_FORMAT=JSON`, so plain `java -jar` works (DIND#19).
- `AI_COMPLETE` answers returned as a JSON string literal are unwrapped before parsing.
- The driver's cloud-metadata probes no longer log ERROR lines at startup.

## [0.10.0] - 2026-10-16

### Added
- `POST /api/products/save`: stores the ODPS product, every ODCS contract and the derived OSI/Apache Ossie model in Snowflake, one row each (YAML text + `VARIANT` document), under a new save id (DIND#6).
- `GET /api/products` (latest save per product) and `GET /api/products/{id}` (reload a save as a validated bundle).
- Table `<db>.DATA_PRODUCTS.ARTIFACTS` created on first use with idempotent DDL; append-only (no UPDATE/DELETE/DROP anywhere), so every save is kept as history. Target database: `STORAGE_DATABASE`, else `SNOWFLAKE_DATABASE`.

## [0.9.0] - 2026-10-16

### Added
- `POST /api/export/osi`: one-way export of the product and its contracts to an OSI/Apache Ossie semantic model (`version: 0.2.0.dev0`), validated against the bundled `ossie-schema.json` (DIND#7).
- Datasets from contract schema objects (`source` = `database.schema.table`, primary keys, descriptions, business names as `ai_context.synonyms`); fields with Ossie datatypes; ODCS classification, tags and quality rules carried in an `ODCS` custom extension.
- Relationships inferred from matching key names (flagged as inferred, not declared).

## [0.8.0] - 2026-10-16

### Added
- Frontend wizard steps 3–5 (DIND#9):
  - **Analyze**: "Analyze with Cortex" (`POST /api/enrich`) with elapsed-time progress; proposals as cards grouped by contract and table, with kind badge, target column and rationale; Accept / Edit / Reject per card, "Accept all for this table" / "Reject all", Undo. Only explicitly accepted proposals (with any edited value) are sent to `POST /api/products/apply`; the YAML is refreshed afterwards.
  - **Verify**: "Run quality rules" (`POST /api/quality/run`) with passed / failed / pass-rate scoreboard and a per-rule table (contract, table, column, rule, observed, threshold, SQL, error); "only failures" filter.
  - **Publish**: "Save to Snowflake" (`POST /api/products/save`), "Export OSI/Apache Ossie" (`POST /api/export/osi`) viewable with validity status, Copy and Download, and the list of saved products (`GET /api/products`) with Open (`GET /api/products/{id}`).
- Next-step button under each step; steps keep their state while moving around the wizard.

### Changed
- `src/api/` sends single-bundle request bodies as both the bundle itself and `{bundle}`, so they match the backend as built and the agreed contract.

## [0.7.0] - 2026-10-16

### Added
- `POST /api/quality/run`: executes every ODCS quality rule in the bundle against Snowflake and reports pass/fail, observed value, threshold and the exact SQL (DIND#5).
- Library metrics `rowCount`, `nullValues`, `missingValues`, `duplicateValues`, `invalidValues` (valid values or regex) compile to SQL; `sql` rules run with `${table}` / `${column}` filled in, and must be a single read-only SELECT.
- Every operator (`mustBe`, `mustNotBe`, `mustBe[Greater|Less]Than`, `...OrEqualTo`, `mustBe[Not]Between`); per-rule query timeout (`quality.timeout-seconds`, default 60); a failing rule never stops the run.

## [0.6.0] - 2026-10-16

### Added
- `POST /api/enrich`: profiles every table in the bundle and asks Cortex (`AI_COMPLETE`, one call per table, 4 in parallel) for descriptions, business names, classifications (PII → `restricted` + `pii` tag), ODCS quality rules and SLAs; returns reviewable proposals with rationale (DIND#4).
- `POST /api/products/apply`: writes only the accepted proposals into a copy of the contracts and re-validates against ODCS v3.2.0.
- Defensive parsing: malformed answers become warnings, unknown columns are dropped, quality rules are reduced to valid ODCS (one operator) and SQL rules must be a single read-only SELECT using `${table}`.

## [0.5.0] - 2026-10-16

### Added
- Frontend "Contract Forge" wizard, replacing the Cortex prompt demo (DIND#8):
  - **Source**: database picker, schema checkboxes (one ODCS contract each), expandable table checkboxes with kind, column and row counts; all tables selected by default.
  - **Generate**: calls `POST /api/products/generate`, renders the ODPS product and each ODCS contract as YAML tabs (`POST /api/products/yaml`), validation badge plus a visible error table, Copy / Download per document and Download all.
- `src/api/` typed client for the whole backend contract; backend errors (`{message}` or ProblemDetail `{detail}`) surface as visible banners.
- `npm run dev:mock`: dev-only in-memory backend with an illustrative `FINSERV_DW` sample, loaded through a guarded dynamic import so it never ships in a build.
- System light/dark appearance via `prefers-color-scheme`; projector-friendly sizing.

### Removed
- `src/api/cortex.ts` prompt demo client.

## [0.4.0] - 2026-10-16

### Added
- `POST /api/profile`: per-column null count and ratio, approximate distinct count, min/max, max text length, top 5 values for low-cardinality columns and 5 sample values (DIND#2).
- Bounded by construction: one aggregate pass per table; tables over `profile.sample-rows` (default 100,000) read via `SAMPLE (n ROWS)`, views via `LIMIT`; every result says whether it was `sampled`.

## [0.3.0] - 2026-10-16

### Added
- `POST /api/products/generate`: database → ODPS v1.1.0 product; each selected schema → ODCS v3.2.0 contract exposed as an output port (`contractId`); tables → contract `schema[]` objects with typed properties, primary keys and a Snowflake `servers` entry (DIND#3).
- `POST /api/products/yaml`: YAML rendering of the product and each contract.
- Every generated document is validated against the official ODCS v3.2.0 and ODPS v1.1.0 JSON schemas (bundled under `standards/`); errors are returned with document and path.

## [0.2.0] - 2026-10-16

### Added
- Catalog API: `GET /api/catalog/databases`, `.../{db}/schemas`, `.../{db}/schemas/{schema}/tables` with columns, row counts, declared primary and foreign keys. Metadata queries only, no data scans (DIND#1).
- Identifier validation: Snowflake object names from the browser are pattern-checked and double-quoted before reaching SQL; anything else is a 400.
- Offline `local` profile serves a stub shaped like the hackathon `FINSERV_DW` database.
- API errors are returned as `{"message": ...}`; Snowflake failures as 502 with Snowflake's reason.
- `backend/.env` (gitignored) is read for Snowflake credentials.
- JaCoCo gate: `./mvnw verify` fails below 60% line coverage (live-only `Jdbc*` adapters excluded).
