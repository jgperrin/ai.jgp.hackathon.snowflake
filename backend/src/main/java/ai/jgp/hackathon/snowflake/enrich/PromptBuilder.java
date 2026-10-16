package ai.jgp.hackathon.snowflake.enrich;

import java.util.stream.Collectors;

import ai.jgp.hackathon.snowflake.profile.ProfileModel.ColumnProfile;
import ai.jgp.hackathon.snowflake.profile.ProfileModel.TableProfile;

/** The prompt sent to Cortex for one table: its shape, its profile, and the exact JSON we expect back. */
final class PromptBuilder {

    private PromptBuilder() {
    }

    static String build(String database, String schema, TableProfile table) {
        String columns = table.columns().stream().map(PromptBuilder::column).collect(Collectors.joining("\n"));
        return """
                You are a senior data steward writing an ODCS v3.2 data contract for a Snowflake table.
                Analyze the table profile below and propose business metadata, data quality rules and SLAs.

                Table: %s.%s.%s
                Row count: %s (profile %s, %d rows examined)
                Columns (name | type | nulls | distinct | min | max | top values | samples):
                %s

                Rules:
                - Base every proposal on the profile. Do not invent columns. Use column names exactly as given.
                - Descriptions: one clear business sentence. Business names: Title Case, human friendly.
                - classification: one of public, internal, confidential, restricted. Mark personal data (names, email,
                  phone, birth date, address, government ids) restricted and set "pii": true.
                - qualityRules: 3 to 8 rules that the profile shows currently HOLD. Prefer:
                  * library rules: {"column": "COL", "type": "library", "metric": "nullValues" | "duplicateValues" |
                    "invalidValues" | "rowCount", one operator, "unit": "rows"} ; for invalidValues add
                    "arguments": {"validValues": [...]} (low-cardinality columns) or {"pattern": "regex"}.
                  * sql rules for cross-column invariants (e.g. one date always after another, amounts within a range):
                    {"column": null, "type": "sql", "query": "SELECT COUNT(*) FROM ${table} WHERE <violation>", "mustBe": 0}
                    The query must count VIOLATIONS, use ${table} as the table placeholder, and be a single SELECT.
                  * operators: exactly one of mustBe, mustBeLessThan, mustBeLessOrEqualTo, mustBeGreaterThan,
                    mustBeGreaterOrEqualTo, mustBeBetween [low, high].
                  * Each rule has "description", "dimension" (accuracy, completeness, conformity, consistency, coverage,
                    timeliness, uniqueness) and "severity" (error or warning) and a one-line "rationale".
                - sla: 1 to 3 items: {"property": "freshness" | "latency" | "retention" | "frequency" | "availability",
                  "value": number or string, "unit": "d" | "h" | "m" | "y" (when relevant), "element": "TABLE.COLUMN"
                  (when relevant), "description": "...", "rationale": "..."}.

                Return ONLY a JSON object, no prose, no markdown fences:
                {"table": {"description": "...", "businessName": "..."},
                 "columns": [{"name": "COL", "description": "...", "businessName": "...", "classification": "...",
                              "pii": false, "tags": ["..."]}],
                 "qualityRules": [...],
                 "sla": [...]}
                """.formatted(database, schema, table.table(), table.rowCount() == null ? "unknown" : table.rowCount(),
                table.sampled() ? "sampled" : "full", table.sampleSize(), columns);
    }

    static String column(ColumnProfile c) {
        String top = c.topValues().stream().map(v -> v.value() + "(" + v.count() + ")")
                .collect(Collectors.joining(", "));
        String samples = c.samples().stream().map(s -> s == null ? "null" : truncate(s))
                .collect(Collectors.joining(", "));
        return "- %s | %s | %d (%.1f%%) | %d | %s | %s | %s | %s".formatted(c.column(), c.dataType(), c.nullCount(),
                100 * c.nullRatio(), c.distinctCount(), truncate(c.min()), truncate(c.max()), top, samples);
    }

    static String truncate(String s) {
        if (s == null) {
            return "-";
        }
        return s.length() > 40 ? s.substring(0, 40) + "…" : s;
    }
}
