# Changelog

All notable changes to this project are documented here. Backend and frontend versions move in lockstep.

## [0.10.48] - 2026-10-17

### Added
- The RFC-0038 context blocks are filled (DIND#109). Cortex proposes one per table —
  instructions for an AI agent, the canonical questions the table answers, and what an
  agent must not do — reviewed like every other proposal and landing on
  `schema[].context`. The contract's own block, the ODPS product's and each output
  port's are then composed from the accepted table blocks: instructions joined per
  table, statements and constraints concatenated and tagged with the table they came
  from. Nothing accepted means no `context` key at any of the four levels, never an
  empty block.
- A context block is edited through its own form (instructions, a question and answer
  list capped at four, a constraint list capped at three) in a section of its own in
  the review, rather than as raw JSON.

### Changed
- The OSI/Apache Ossie export carries the product's context in the shape Ossie has for
  it: its instructions appended to `ai_context.instructions` and its canonical
  questions as `ai_context.examples`. Ossie's `AIContext` has no slot for the
  constraints, and none is invented for it — the contracts carry them and stay the
  source of truth.

### Fixed
- The OSI instructions read "Semantic model of the FINSERV_DW data product data
  product": the exporter appended the noun that DIND#110 had just put into the
  product's name. Caught by the test written for the change above.

## [0.10.47] - 2026-10-17

### Changed
- A DQ rule or an SLA opens in a form instead of a JSON textarea (DIND#104). The
  form covers what the backend accepts: for a library rule the metric, the
  operator and its threshold (a low and a high for the between ones), the
  valid-values list or pattern an `invalidValues` rule needs, and the description,
  dimension, severity and unit; for a SQL rule the query, checked to be one
  read-only SELECT before it is sent; for an SLA the property as a list, its
  value, unit, element and description. An incomplete rule is refused with the
  reason rather than an "Invalid JSON" line, and the raw JSON editor stays one
  click away for the ODCS fields the form does not cover.

### Note
- The form model lives in `frontend/src/lib/ruleForm.ts`, and `QualityRulesTest`
  pins every shape it can emit, so the form cannot produce a rule the backend
  then drops.

## [0.10.46] - 2026-10-17

### Added
- Every view and wizard step has its own URL (DIND#101): `/source`, `/generate`,
  `/analyze`, `/verify`, `/publish`, `/store`, `/store/<saveId>` and `/about`, all
  built from the app's base path so one build serves any prefix. Back and Forward
  move between them, a reload keeps its step, and the stepper, the brand, the
  "Saved in Snowflake" button and the Next button are real links — middle-click
  and open-in-new-tab work. A step URL with no product in memory (a reload, or a
  link someone pasted) lands on Source and says why rather than showing an empty
  step, and `/store/<saveId>` opens that save in the wizard while a bad id leaves
  the reader in the store with the reason. No server change was needed: the nginx
  SPA fallback already serves `index.html` for any path under the prefix.
- `react-router-dom`, the app's first runtime dependency beyond React. It is used
  for the base-path-aware links and history, not for route-driven rendering — the
  steps stay mounted, because the bundle lives in memory and unmounting would
  throw it away. The bundle grows from 89 kB to 103 kB gzipped.

## [0.10.45] - 2026-10-17

### Fixed
- The README and `InMemoryArtifactStore` still described the store as append-only
  (DIND#111): "append-only" in the architecture diagram, "storage never updates or
  deletes" under *Safe by construction*, and "same append-only behaviour" on the
  offline stand-in. A stored document is never overwritten, which is the property
  worth stating, and a save can be deleted whole.

### Note
- No artifact change: documentation and one comment.

## [0.10.44] - 2026-10-17

### Added
- A save can be deleted from the store (DIND#111): `DELETE /api/products/{saveId}` removes
  every artifact row under that save, and the store view puts a Delete on each row behind a
  confirmation naming the product, its version, when it was saved, who saved it and how many
  artifacts go with it. A save is deleted whole, never one artifact of three — that would
  leave a product whose contract is gone. An unknown or malformed id is refused and deletes
  nothing.

### Changed
- `StoreSql`'s "creates, inserts and reads; never updates or deletes" note is replaced by the
  rule that actually holds: a stored document is never overwritten, and a save can be deleted
  whole because a user asked for it.

## [0.10.43] - 2026-10-17

### Fixed
- Verify says why it has nothing to run (DIND#105). The report was "DQ rules are
  not executed", and reproducing it against the deployed build showed the
  pipeline is healthy end to end: Cortex proposes the rules, Apply puts them in
  the contract, and all six ran on Snowflake. What the session actually had was
  proposals accepted and never applied, and the step said only "Accept some DQ
  rules in Analyze first" — which reads as if that had not been done. It now
  names the case: "N accepted proposals are waiting in Analyze" versus having
  accepted nothing at all. Analyze reports its accepted-but-unapplied count up
  through the wizard so the two can be told apart.

## [0.10.42] - 2026-10-17

### Changed
- Products and contracts are named for what they are, and their ids are derived
  from that name (DIND#110). The product is `<Database> data product` and each
  contract is `<Database>.<Schema> data contract` — both used to be the bare
  database name, so the store's Product and Id columns said the same thing twice.
  Their ids are now a UUID v5 (name-based over SHA-1, RFC 4122) of that name in a
  namespace this app owns, instead of the slugified database name: deterministic,
  so regenerating the same database or schema yields the same id, and two
  different names can no longer collide the way a slug can. Table, column and
  output-port ids keep their slug form, and `servers[0].schema` is read directly
  rather than sliced out of the contract name.
- The OSI/Apache Ossie model is named from the product's name, not its id —
  which is a UUID now, and no use as a model name.
- Saves already in the lab account still open and still list: the store is keyed
  by `SAVE_ID`, so ids are not migrated.

## [0.10.41] - 2026-10-16

### Added
- A second instance, at `tools.actianlabs.com/contextbuilder-vnext` (DIND#106).
  The frontend's base path is a build input (`VITE_BASE`; the default and every
  existing build are unchanged), `deploy.sh` and `rollback.sh` take an instance
  slug and port and derive the `/opt` directory, static directory, unit name,
  public URL and Vite base from it, `deploy/systemd/contextbuilder-vnext.service`
  and an nginx tenant for it are in the repo, and the sign-in cookie's name is a
  setting (`AUTH_COOKIE_NAME`) because two instances on one host sharing
  `cb_signin` would sign each other's users out.
- The ops vhost mirrors are byte-identical again: the ops and CRM copies had
  never received the `contextbuilder` tenant, so all three now carry both tenants
  (the drift #75 recorded).

## [0.10.40] - 2026-10-16

### Added
- A category filter over the Cortex review (DIND#103): pills above the review,
  one per category actually proposed and carrying its count, plus "All
  categories". Picking one narrows the review to the categories picked, which is
  how a 56-card table becomes readable. The filter changes what is drawn and
  nothing else — the decisions, the counts in the review bar and what Apply sends
  are identical with and without it, and the per-table Accept all still covers the
  whole table while showing "12 of 56 shown" so it is clear what is hidden. A
  re-analysis clears the filter.

## [0.10.39] - 2026-10-16

### Changed
- The Cortex review is grouped by category (DIND#102): inside each table, one
  section per proposal kind in a fixed order — Primary key, Description, Business
  name, Classification, Tag, DQ rule, SLA — each headed by its name and count and
  carrying its own Accept all / Reject all. A bulk accept can now be aimed at one
  kind instead of sweeping in the DQ rules and SLAs nobody has read yet. The
  per-table Accept all / Reject all stay, and a kind the fixed order does not know
  is appended after it rather than dropped, so a new proposal kind cannot make its
  cards disappear.

### Fixed
- `ProposalKind` gains `primaryKey` (DIND#102): the backend has always sent those
  proposals, and the type did not admit them, so the cards fell back to rendering
  the raw kind string.

## [0.10.38] - 2026-10-16

### Added
- The demo video is the first thing on the README, on the line under the title
  (DIND#97): "Watch the demo: Creating the context layer using ODCS and ODPS",
  linking to https://youtu.be/Rg0gxIELAkU. The link text is the video's own title.

### Note
- No artifact change: documentation only.

## [0.10.37] - 2026-10-16

### Changed
- The About poster ships as WebP (DIND#93): `frontend/src/assets/about/github2.webp`,
  encoded from the same master at 1774x887 and quality 82, is 384 KB against the
  JPEG's 917 KB. The JPEG is dropped rather than kept as a fallback, because every
  browser this app can run in decodes WebP — one asset, not two. `art/github2.jpg`
  is the untouched master and is unchanged.

## [0.10.36] - 2026-10-16

### Added
- `doc/SUBMISSION.md`, the Devpost story for the Data in the D hackathon, linked
  from the README (DIND#99). It had been committed on a local checkout only, at
  15:04; this lands it on `main`.

### Note
- No artifact change: documentation only.

## [0.10.35] - 2026-10-16

### Changed
- About is a window over the poster (DIND#91): drag it by its title bar
  anywhere in the content area (clamped, so it can't be lost), and it's
  translucent glass with a blurred backdrop, so the artwork shows through. The
  page scrim is much lighter. Text holds WCAG AA on the glass over any
  backdrop; reduced-transparency and no-`backdrop-filter` fall back to an
  opaque surface. At phone width it sits in flow and does not drag.

## [0.10.34] - 2026-10-16

### Changed
- The standards pills under the headline are links (DIND#94): Bitol ODCS v3.2
  and Bitol ODPS v1.1 open the Bitol repositories README.md already cites, and
  OSI/Apache Ossie opens `apache/ossie`. New tab, same look, visible focus ring.

## [0.10.33] - 2026-10-16

### Fixed
- The Source step also hides `USER$<name>`, the per-user scratch database as
  the lab account actually names it (`USER$JGP`). #62's rule only covered
  `USERS$*`, so a live smoke check still listed it among the account's
  databases (DIND#86).

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
