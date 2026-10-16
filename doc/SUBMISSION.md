# Context Builder: hackathon submission text

Draft of the Devpost story for the **Data in the D** hackathon (project code `DIND`). Seven
sections, matching the Devpost form. Not yet pasted into Devpost.

Every factual claim below is traceable: the numbers come from the repo, and the Challenges
section links to the CHANGELOG entry that records each fix.

## Inspiration

Everyone says "build a context layer for AI." Nobody shows you one. What they show you instead is a proprietary silo, written by hand by whichever team drew the short straw, stale by the following Tuesday.

We work on the open standards that already describe this: ODCS for data contracts, ODPS for data products, OSI and Apache Ossie for semantic models. The standards aren't the problem. Filling them in is the kind of work that makes people change careers.

Meanwhile Cortex sits inside Snowflake, able to read your schema and sample your data and form strong opinions about both. So: let the model draft the boring parts, let a human approve them, and write the result in open standards so it's worth something outside our UI.

## What it does

Point it at a Snowflake database. Five steps later you have a governed data product.

1. **Source.** Browse the catalog: databases, schemas, tables, columns, row counts, keys. Metadata queries only. We don't read your data to populate a dropdown.
2. **Generate.** One ODPS v1.1 product plus one ODCS v3.2 contract per schema, wired together as output ports, checked against the official JSON schemas.
3. **Analyze.** Cortex profiles each table and proposes descriptions, business names, PII classifications, tags, quality rules, SLAs and primary keys, each with a rationale. They arrive as cards you accept, edit or reject. It does correctly spot that `customer_email` is PII, which is a low bar and yet.
4. **Verify.** Accepted rules compile to SQL and run against Snowflake for real: pass or fail, observed value, threshold, and the SQL itself, so you can argue with it.
5. **Publish.** Everything is saved to Snowflake as versioned artifacts that only ever grow, plus an OSI and Apache Ossie semantic model export.

Judges: sign in at the live URL with `jgp` / `odcs`. The demo profile already carries the hackathon lab account's Snowflake connection, and any user can point it at their own account from the header. No Snowflake account at all? The `local` profile stubs `FINSERV_DW` and the whole wizard still works. A demo that runs without a database is the only kind worth trusting.

## How we built it

Java 21, Spring Boot 4.1.1, Maven. React 19, Vite, TypeScript. Snowflake over JDBC, Cortex through SQL `AI_COMPLETE`.

Everything that talks to Snowflake sits behind an interface with two implementations: the real one, and a stub Spring picks under the `local` profile. That's why it boots with no credentials, and why the front end can't tell the difference.

82 tests, 24 pull requests, 25 issues, about 8,300 lines, every change behind a branch and a green build with a coverage gate at 60%. The commit history spans 1 hour 43 minutes, which is either impressive or a warning sign depending on who's reading it.

The JSON schemas bundled in the repo are the official ones. We didn't write a looser schema we could pass.

## Challenges we ran into

**Cortex stopped halfway through a sentence.** `AI_COMPLETE` was hitting a default output cap and returning JSON that ended in the middle of an object, and one malformed answer cost us a whole table of proposals. Fixed with an explicit budget of 8,192 tokens, temperature 0, a prompt that asks for shorter text, and one retry that logs the length and the tail of the bad answer so you can see where it gave up. ([0.10.2](https://github.com/jgperrin/ai.jgp.hackathon.snowflake/blob/main/CHANGELOG.md#0102---2026-10-16))

**A whole schema in one HTTP request was a single point of failure.** Analysis takes 30 to 40 seconds on real tables, and behind a proxy with a short read timeout the old single request came back as a bare 502 with nothing to show for the wait. It runs as a background job now, with a status endpoint the browser polls, so a slow proxy can no longer throw away finished work. ([0.10.14](https://github.com/jgperrin/ai.jgp.hackathon.snowflake/blob/main/CHANGELOG.md#01014---2026-10-16))

**Quality rules aren't SQL, so we wrote a compiler.** ODCS describes quality as a library of metrics and operators, not queries: `rowCount`, `nullValues`, `duplicateValues`, every `mustBe…` variant. Each one compiles down to SQL, and anything the model proposes that isn't a single statement starting with SELECT or WITH, with no semicolon and no write keyword in it, is rejected before it runs. ([0.7.0](https://github.com/jgperrin/ai.jgp.hackathon.snowflake/blob/main/CHANGELOG.md#070---2026-10-16), [0.6.0](https://github.com/jgperrin/ai.jgp.hackathon.snowflake/blob/main/CHANGELOG.md#060---2026-10-16))

**Three return shapes, one parser.** `AI_COMPLETE` answers as plain text, as a `choices` envelope, or as a JSON string literal you have to unwrap first. ([0.10.2](https://github.com/jgperrin/ai.jgp.hackathon.snowflake/blob/main/CHANGELOG.md#0102---2026-10-16), [0.10.1](https://github.com/jgperrin/ai.jgp.hackathon.snowflake/blob/main/CHANGELOG.md#0101---2026-10-16))

**Java 21 and the driver disagreed about Arrow.** Live queries died decoding results until connections switched to `JDBC_QUERY_RESULT_FORMAT=JSON`, which beat shipping a start command that only works with the right JVM flags attached. ([0.10.1](https://github.com/jgperrin/ai.jgp.hackathon.snowflake/blob/main/CHANGELOG.md#0101---2026-10-16))

**Deploying under a subpath.** The tool lives at `/contextbuilder` on a box already serving two other tools, so: Vite `base`, API paths relative to the base, and an nginx proxy that strips the prefix. Also a config check before every reload. The deploy notes are blunt that a misplaced brace takes the whole host down, the other tools included. ([0.10.5](https://github.com/jgperrin/ai.jgp.hackathon.snowflake/blob/main/CHANGELOG.md#0105---2026-10-16))

## Accomplishments that we're proud of

- **It found rules nobody told it about.** On the hackathon's `FINSERV_DW` lab data, Cortex rediscovered invariants the dataset authors had planted: that a claim cannot be reported before the incident occurred, that an effective date precedes an expiration date. On `INSURANCE`, 104 proposals in 30 seconds with no warnings, and all 12 proposed quality rules passed when we ran them against the data.
- **The loop closes.** Schema, then contracts, then Cortex proposals, then rules that actually execute, then artifacts in Snowflake, then a semantic model export. Nobody has to squint and imagine the missing part.
- **Validated against the official schemas**, not one we wrote to match our own output.
- **The human is in the loop by construction.** Cortex proposes, you decide, only the cards you accept get written.
- **Live for the judges** at `tools.actianlabs.com/contextbuilder`, signed in as `jgp` / `odcs` with a Snowflake connection to the lab account already set up, and each user can point it at their own Snowflake account from the header.

## What we learned

- Context layers are a pipeline problem, not a documentation problem. Annoying, because documentation problems are easier. If it can't be generated, reviewed and regenerated, it rots, and the standards stay as empty as they've always been.
- Cortex is genuinely good at the semantic layer: naming things, describing things, spotting PII, proposing a threshold and then proving it. It is not careful enough to write straight into a governance artifact, so the validator has to be code you would trust with the model switched off.
- Constraints beat prompts. Bounding profiling with `SAMPLE (n ROWS)` and forcing every rule into a single SELECT bought more reliability than any amount of careful prompt wording.
- Design for the Snowflake surface you actually get. `AI_COMPLETE` over JDBC is throughput shaped and model choice is explicit on every call, while the analyst grade surface lives behind REST. Building that seam on day one is why streaming into the UI is a next step rather than a rewrite.
- Open standards are the API. Because the output is ODCS, ODPS and Ossie, the tool is useful even if you never open our UI.

## What's next for Context Builder

Today is the Snowflake slice: semantics, quality rules, SLAs, primary keys and inferred relationships. The rest of the picture:

- **Snowflake native identity.** Each user already signs in and stores their own Snowflake connection, one pool per user, with the password write only and never returned or logged. Next is OAuth and key pair authentication, so no password is stored at all, Snowflake's own roles and grants decide what each person can see, and every query in the account history carries the real user's name.
- **More sources.** Files, streams, applications, APIs, existing metadata catalogs.
- **The rest of the layer.** Ownership, lineage, usage and support. The contract fields exist. The proposers don't yet.
- **Make "maintain" real.** Profiling on a schedule and drift detection, so context refreshes instead of being rebuilt by whoever is on call.
- **Close the loop on ourselves.** Stream via Cortex REST, then point Cortex Analyst at the semantic model we just generated. Build the context, then use it, without leaving the tool.
- **Round trip the standards.** Import contracts you already have, diff them against what the data says now, and file the difference as review cards.

Point it at your data, get a context layer that's current, approved by a human, and portable because it's written in standards nobody owns.
