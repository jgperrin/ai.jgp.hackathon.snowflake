package ai.jgp.hackathon.snowflake.cortex;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Active under the 'local' profile: no Snowflake account, no network, no credits spent.
 * Exists so the UI can be built and demoed before credentials are wired up. Enrichment
 * prompts get a canned answer shaped like a real one.
 */
@Component
@Profile("local")
class StubCortexClient implements CortexClient {

    @Override
    public String complete(String prompt) {
        if (prompt.contains("Return ONLY a JSON object")) {
            return prompt.contains("INCIDENT_DATE") ? CLAIMS : GENERIC;
        }
        return "Stubbed answer (no Snowflake connection). You asked: " + prompt;
    }

    private static final String GENERIC = """
            {"table": {"description": "Stub description (no Snowflake connection)."}}""";

    private static final String CLAIMS = """
            ```json
            {"table": {"description": "Insurance claims filed against policies, one row per claim.",
                       "businessName": "Insurance Claims"},
             "columns": [
               {"name": "CLAIM_ID", "description": "Unique claim identifier.", "businessName": "Claim ID",
                "classification": "internal", "pii": false, "tags": ["identifier"]},
               {"name": "INCIDENT_DATE", "description": "Date the insured incident happened.",
                "businessName": "Incident Date", "classification": "internal", "pii": false}],
             "qualityRules": [
               {"column": "CLAIM_ID", "type": "library", "metric": "duplicateValues", "mustBe": 0, "unit": "rows",
                "description": "Claim IDs are unique.", "dimension": "uniqueness", "severity": "error",
                "rationale": "Primary key with as many distinct values as rows."},
               {"column": null, "type": "sql",
                "query": "SELECT COUNT(*) FROM ${table} WHERE REPORTED_DATE < INCIDENT_DATE", "mustBe": 0,
                "description": "A claim is never reported before the incident.", "dimension": "consistency",
                "severity": "error", "rationale": "REPORTED_DATE is always on or after INCIDENT_DATE in the profile."},
               {"column": "CLAIM_STATUS", "type": "library", "metric": "invalidValues",
                "arguments": {"validValues": ["OPEN", "APPROVED", "DENIED", "PAID"]}, "mustBe": 0,
                "description": "Status is one of the known values.", "dimension": "conformity", "severity": "warning"}],
             "sla": [{"property": "freshness", "value": 1, "unit": "d", "element": "CLAIMS.REPORTED_DATE",
                      "description": "New claims visible within a day.", "rationale": "Claims are operational."}]}
            ```""";
}
