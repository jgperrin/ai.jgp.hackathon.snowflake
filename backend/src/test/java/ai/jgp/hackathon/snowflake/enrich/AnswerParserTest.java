package ai.jgp.hackathon.snowflake.enrich;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import ai.jgp.hackathon.snowflake.profile.ProfileModel.ColumnProfile;
import ai.jgp.hackathon.snowflake.profile.ProfileModel.TableProfile;
import ai.jgp.hackathon.snowflake.profile.ProfileModel.ValueCount;

class AnswerParserTest {

    static final Set<String> COLUMNS = Set.of("CLAIM_ID", "INCIDENT_DATE", "REPORTED_DATE", "CLAIM_STATUS", "EMAIL");

    AnswerParser.Result parse(String answer) {
        return AnswerParser.parse("c1", "CLAIMS", COLUMNS, answer, new AtomicInteger());
    }

    /** The RFC-0038 block arrives as its own proposal, table-level, cleaned (DIND#109). */
    @Test
    @SuppressWarnings("unchecked")
    void parsesAContextBlock() {
        AnswerParser.Result r = parse("""
                {"table": {"description": "Claims."},
                 "context": {"instructions": "Use CLAIMS for policy questions.",
                   "verifiedStatements": [{"question": "How many claims?", "answer": "One per policy."}],
                   "constraints": [{"constraint": "Do not sum PREMIUM."}],
                   "rationale": "Agents ask this first."}}
                """);

        assertThat(r.warnings()).isEmpty();
        assertThat(r.proposals()).extracting(Proposal::kind).containsExactly("description", "context");
        Proposal context = r.proposals().get(1);
        assertThat(context.column()).isNull();
        assertThat(context.rationale()).isEqualTo("Agents ask this first.");
        Map<String, Object> block = (Map<String, Object>) context.value();
        assertThat(block).containsEntry("instructions", "Use CLAIMS for policy questions.");
        assertThat((java.util.List<Object>) block.get("verifiedStatements")).hasSize(1);
    }

    /** An empty block is not a proposal: it would put a `context: {}` on the table. */
    @Test
    void anEmptyContextBlockIsAWarningRatherThanAProposal() {
        AnswerParser.Result r = parse("{\"table\": {}, \"context\": {\"instructions\": \"   \"}}");

        assertThat(r.proposals()).extracting(Proposal::kind).doesNotContain("context");
        assertThat(r.warnings()).anyMatch(w -> w.contains("empty context block"));
    }

    @Test
    void parsesFencedAnswerIntoProposals() {        String answer = "Here you go:\n```json\n" + """
                {"table": {"description": "Claims.", "businessName": "Claims"},
                 "columns": [{"name": "EMAIL", "description": "Contact email.", "classification": "Restricted",
                              "pii": true, "tags": ["Contact Info"]}],
                 "qualityRules": [{"column": "CLAIM_ID", "metric": "duplicateValues", "mustBe": 0},
                   {"column": null, "type": "sql", "query": "SELECT COUNT(*) FROM ${table} WHERE REPORTED_DATE < INCIDENT_DATE;",
                    "mustBe": 0, "dimension": "Consistency", "rationale": "Dates ordered."}],
                 "sla": [{"property": "freshness", "value": 1, "unit": "d"}]}
                """ + "```";
        AnswerParser.Result r = parse(answer);
        assertThat(r.warnings()).isEmpty();
        assertThat(r.proposals()).extracting(Proposal::kind).containsExactly("description", "businessName",
                "description", "classification", "tag", "tag", "qualityRule", "qualityRule", "sla");
        assertThat(r.proposals().get(3).value()).isEqualTo("restricted");
        assertThat(r.proposals().get(3).rationale()).contains("PII");
        assertThat(r.proposals().get(4).value()).isEqualTo("pii");
        assertThat(r.proposals().get(5).value()).isEqualTo("contact_info");
        assertThat(r.proposals().get(6).value()).isEqualTo(
                Map.of("type", "library", "metric", "duplicateValues", "mustBe", 0));
        @SuppressWarnings("unchecked")
        Map<String, Object> sql = (Map<String, Object>) r.proposals().get(7).value();
        assertThat(sql).containsEntry("query", "SELECT COUNT(*) FROM ${table} WHERE REPORTED_DATE < INCIDENT_DATE")
                .containsEntry("dimension", "consistency");
        assertThat(r.proposals().get(7).column()).isNull();
        assertThat(r.proposals()).extracting(Proposal::id).doesNotHaveDuplicates();
    }

    @Test
    void toleratesTrailingCommasAndComments() {
        AnswerParser.Result r = parse("""
                {"table": {"description": 'Claims.', }, // the table
                 "columns": [],}""");
        assertThat(r.proposals()).hasSize(1);
        assertThat(r.warnings()).isEmpty();
    }

    @Test
    void malformedAnswerGivesWarningNotException() {
        AnswerParser.Result r = parse("I cannot help with that {not json");
        assertThat(r.proposals()).isEmpty();
        assertThat(r.warnings()).hasSize(1);
        assertThat(parse(null).proposals()).isEmpty();
    }

    @Test
    void unknownColumnsAndUnsafeRulesAreDropped() {
        AnswerParser.Result r = parse("""
                {"columns": [{"name": "GHOST", "description": "x"}],
                 "qualityRules": [{"column": "GHOST", "metric": "nullValues", "mustBe": 0},
                   {"type": "sql", "query": "DELETE FROM ${table}", "mustBe": 0},
                   {"type": "sql", "query": "SELECT 1; DROP TABLE x", "mustBe": 0},
                   {"type": "library", "metric": "magic", "mustBe": 0}],
                 "sla": [{"property": "vibes", "value": 1}]}""");
        assertThat(r.proposals()).isEmpty();
        assertThat(r.warnings()).hasSize(5);
    }

    @Test
    void promptCarriesProfileAndContract() {
        TableProfile t = new TableProfile("CLAIMS", 14000L, false, 14000, java.util.List.of(new ColumnProfile(
                "CLAIM_STATUS", "TEXT", 0, 0, 4, "APPROVED", "PAID", 8,
                java.util.List.of(new ValueCount("OPEN", 7000)), java.util.Arrays.asList("OPEN", null))));
        String prompt = PromptBuilder.build("FINSERV_DW", "INSURANCE", t);
        assertThat(prompt).contains("FINSERV_DW.INSURANCE.CLAIMS").contains("CLAIM_STATUS | TEXT")
                .contains("OPEN(7000)").contains("${table}").contains("Return ONLY a JSON object");
    }
}
