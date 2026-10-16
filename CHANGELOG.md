# Changelog

All notable changes to this project are documented here. Backend and frontend versions move in lockstep.

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
