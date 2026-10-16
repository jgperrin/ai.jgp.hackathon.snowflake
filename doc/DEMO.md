# Context Builder: 3-minute demo

Live at **https://tools.actianlabs.com/contextbuilder/**. Sign in with `jgp` / `odcs`.

Before going on stage: sign in once, open **Snowflake connection**, and check it says *Connected*. Run steps 1 to 4 once on `INSURANCE` so the warehouse is warm.

| Time | Screen | Do | Say |
|---|---|---|---|
| 0:00 | Sign-in | Show the page, sign in | "Everybody wants a context layer for AI. Nobody agrees what it looks like. We build it from your data, with open standards." |
| 0:15 | Source | Pick `FINSERV_DW`, tick `INSURANCE` (and `CORE`) | "The database becomes a data product. Each schema becomes a data contract, exposed as an output port." |
| 0:35 | Generate | Click Generate; open the ODPS tab, then an ODCS tab | "ODPS v1.1 product, ODCS v3.2 contracts, both validated against the official JSON schemas. Green badge." |
| 0:55 | Analyze | Click *Analyze with Cortex* (about 30 to 40 s) | "Snowflake Cortex reads the profile of every table and proposes the semantic layer: descriptions, business names, PII, quality rules, SLAs, primary keys." |
| 1:35 | Analyze | Point at the CLAIMS rule *a claim cannot be reported before the incident occurred*; accept all for CLAIMS; edit one description | "Nobody told it that rule. The dataset authors planted it; Cortex found it in the data. And nothing lands in the contract until a human accepts it." |
| 2:05 | Verify | Click *Run quality rules* | "A rule is only a claim until it runs. Every rule executes on Snowflake, here is the SQL, and they pass." |
| 2:25 | Publish | *Save to Snowflake*, then *Export OSI/Apache Ossie* | "The contracts live in Snowflake, versioned. And the same context goes out as OSI/Apache Ossie, so BI and AI tools can use it." |
| 2:50 | Header | Show *Snowflake connection* | "Bring your own account: each user connects their own Snowflake." |

## If something goes wrong

- **Analyze is slow**: it is one Cortex call per table, four in parallel. Select fewer tables.
- **409 / connection panel opens**: the Snowflake connection is missing or broken; fix it in the panel (*Test & save* shows Snowflake's own error).
- **A table has no proposals**: a warning names it; Cortex answered something that was not valid JSON twice. Run Analyze again.
