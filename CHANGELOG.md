# Changelog

All notable changes to this project are documented here. Backend and frontend versions move in lockstep.

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
