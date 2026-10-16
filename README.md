# ai.jgp.hackathon.snowflake

Hackathon project: an AI application built on **Snowflake Cortex**, with a Spring Boot
backend and a React front end.

The point of this scaffold is that it runs end to end in about two minutes, **without a
Snowflake account**. A `local` profile serves stubbed answers so you can build and demo the
UI first and wire up real credentials later.

## Stack

| Layer | Choice |
|---|---|
| Backend | Java 21, Spring Boot 4.1.1, Maven (wrapper included) |
| Frontend | React 19, Vite 8, TypeScript |
| AI | Snowflake Cortex, called over JDBC — `AI_COMPLETE` |

## Run it (no Snowflake needed)

Two terminals.

```bash
# terminal 1 — backend on :8080
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# terminal 2 — frontend on :5173
cd frontend
npm install
npm run dev
```

Open <http://localhost:5173>, type a prompt, get an answer. The Vite dev server proxies
`/api` to `:8080`, so the browser stays on one origin and CORS never comes up.

You can also hit the API directly:

```bash
curl -s localhost:8080/api/cortex/complete \
  -H 'Content-Type: application/json' \
  -d '{"prompt":"hello"}'
```

```json
{ "answer": "Stubbed answer (no Snowflake connection). You asked: hello" }
```

## Run it against real Cortex

Set these, then start the backend **without** the `local` profile:

| Variable | Notes |
|---|---|
| `SNOWFLAKE_ACCOUNT` | Full account identifier, e.g. `myorg-myaccount` or `xy12345.us-east-1`. `.snowflakecomputing.com` is appended for you. |
| `SNOWFLAKE_USER` | |
| `SNOWFLAKE_PASSWORD` | |
| `SNOWFLAKE_WAREHOUSE` | |
| `SNOWFLAKE_DATABASE` | |
| `SNOWFLAKE_SCHEMA` | Optional |
| `SNOWFLAKE_ROLE` | Optional |
| `CORTEX_MODEL` | Defaults to `claude-sonnet-5` |

```bash
cd backend
SNOWFLAKE_ACCOUNT=myorg-myaccount \
SNOWFLAKE_USER=jgp \
SNOWFLAKE_PASSWORD=... \
./mvnw spring-boot:run
```

The same browser flow now returns live Cortex output.

## What the Snowflake side needs

- **Privileges.** The role needs the `USE AI FUNCTIONS` account privilege and either
  `CORTEX_USER` or `AI_FUNCTIONS_USER`.
- **A model, always.** There is no documented server-side default, so `CORTEX_MODEL` is
  sent explicitly on every call. Check what your region actually offers with:
  ```sql
  SHOW CORTEX BASE MODELS;
  ```
  That also reports `lifecycle_status`, `legacy_date` and `eol_date`, which is worth
  checking before you commit to a model — this space moves fast.
- **Cost.** Cortex is billed by tokens consumed, and model choice swings that a lot. Watch
  `CORTEX_FUNCTIONS_USAGE_HISTORY` and `METERING_DAILY_HISTORY`.

> **Function naming.** This project uses `AI_COMPLETE`. The older
> `SNOWFLAKE.CORTEX.COMPLETE` is legacy and leaves its back-compat window during 2026
> (`COMPLETE`/`TRY_COMPLETE` → `AI_COMPLETE`). If you copy older sample code from the web,
> that is the rename you will trip over.

## Layout

```
backend/
  src/main/java/ai/jgp/hackathon/snowflake/
    cortex/    CortexClient (the seam), JdbcCortexClient, StubCortexClient, controller
    config/    CortexProperties, SnowflakeConfig (Hikari pool)
frontend/
  src/api/cortex.ts   the one place that knows the backend's shape
  src/App.tsx
```

### The seam

`CortexClient` is the only thing the rest of the app knows about:

```java
public interface CortexClient {
    String complete(String prompt);
}
```

Spring picks the implementation by profile — `JdbcCortexClient` normally,
`StubCortexClient` under `local`. Both return the same response shape, so the frontend
cannot tell them apart, and swapping in a REST-backed implementation later means adding a
class rather than touching callers.

## Notes for whoever picks this up next

- **Streaming is not available over JDBC.** SQL `AI_COMPLETE` is batch/throughput shaped;
  if you want tokens streaming into the UI you need the Cortex REST API
  (`POST /api/v2/cortex/v1/chat/completions`), which means a second auth path.
- **Cortex Analyst has no SQL function** — it is REST-only. Cortex Search is reachable both
  ways. If the hackathon idea heads toward natural-language-over-data, this is the fork in
  the road, and it is worth deciding early rather than discovering late.
- **Snowflake JDBC 4.x moved its public API** to `net.snowflake.client.api.*`. The driver is
  deliberately *not* pinned by class name in `SnowflakeConfig`; Hikari resolves it from the
  `jdbc:snowflake://` URL instead, so a driver upgrade will not break startup.

## Tests

```bash
cd backend && ./mvnw test     # context loads, stub client wired, endpoint answers
cd frontend && npm run build  # type-checks and builds
```
