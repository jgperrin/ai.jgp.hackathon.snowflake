# Changelog

All notable changes to this project are documented here. Backend and frontend versions move in lockstep.

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
