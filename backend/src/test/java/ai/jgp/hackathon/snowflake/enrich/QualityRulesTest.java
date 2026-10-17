package ai.jgp.hackathon.snowflake.enrich;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QualityRulesTest {

    /**
     * Every shape the frontend's rule form can emit (DIND#104), and each one must survive
     * {@code sanitize} unchanged — the form can never produce a rule the backend then drops.
     * `frontend/src/lib/ruleForm.ts` is the other half of this contract; if a field is added
     * there, it belongs here too.
     */
    @Test
    void everyShapeTheFormCanEmitSurvivesSanitizeUnchanged() {
        for (Map<String, Object> emitted : List.<Map<String, Object>>of(
                // rowCount needs no column and no arguments
                Map.of("type", "library", "metric", "rowCount", "mustBe", 0),
                // the usual column rule, with everything the form can fill in
                Map.of("type", "library", "metric", "nullValues", "mustBe", 0, "description", "Never null.",
                        "dimension", "completeness", "severity", "error", "unit", "rows"),
                Map.of("type", "library", "metric", "duplicateValues", "mustNotBe", 3, "unit", "rows"),
                Map.of("type", "library", "metric", "missingValues", "mustBeGreaterThan", 0),
                Map.of("type", "library", "metric", "nullValues", "mustBeBetween", List.of(0, 5)),
                Map.of("type", "library", "metric", "invalidValues", "mustBe", 0,
                        "arguments", Map.of("validValues", List.of("CASH", "LEASED"))),
                Map.of("type", "library", "metric", "invalidValues", "mustBe", 0,
                        "arguments", Map.of("pattern", "^[0-9]+$")),
                Map.of("type", "sql", "query", "SELECT COUNT(*) FROM ${table} WHERE APR IS NULL", "mustBe", 0,
                        "description", "APR and term must agree."),
                Map.of("type", "sql", "query", "SELECT COUNT(*) FROM ${table}", "mustBeLessThan", 100))) {
            assertThat(QualityRules.sanitize(emitted)).isEqualTo(emitted);
        }
    }

    /** The form's own refusals, checked at the boundary: these are what it must never send. */
    @Test
    void shapesTheFormRefusesAreAlsoRefusedBySanitize() {
        // invalidValues with neither a list nor a pattern
        assertThat(QualityRules.sanitize(Map.of("type", "library", "metric", "invalidValues", "mustBe", 0))).isNull();
        // an unknown metric
        assertThat(QualityRules.sanitize(Map.of("type", "library", "metric", "whatever", "mustBe", 0))).isNull();
        // a SQL rule that is not one read-only SELECT
        assertThat(QualityRules.sanitize(Map.of("type", "sql", "query", "DELETE FROM t", "mustBe", 0))).isNull();
        assertThat(QualityRules.sanitize(Map.of("type", "sql", "query", "SELECT 1; DROP TABLE t", "mustBe", 0)))
                .isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SELECT COUNT(*) FROM t", "  select 1 ;", "WITH x AS (SELECT 1) SELECT * FROM x"})
    void readOnlySelects(String q) {
        assertThat(QualityRules.isReadOnlySelect(q)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"DELETE FROM t", "SELECT 1; DELETE FROM t", "SELECT * FROM t WHERE 1=1 OR DROP",
        "CALL proc()", "UPDATE t SET a=1", ""})
    void refusesWrites(String q) {
        assertThat(QualityRules.isReadOnlySelect(q)).isFalse();
    }

    @Test
    void keepsExactlyOneOperator() {
        Map<String, Object> rule = QualityRules.sanitize(Map.of("metric", "nullValues", "mustBe", 0,
                "mustBeLessThan", 5));
        assertThat(rule).containsEntry("mustBe", 0).doesNotContainKey("mustBeLessThan");
    }

    @Test
    void betweenNeedsTwoOrderedNumbers() {
        assertThat(QualityRules.sanitize(Map.of("metric", "rowCount", "mustBeBetween", List.of(10, 100))))
                .containsEntry("mustBeBetween", List.of(10, 100));
        assertThat(QualityRules.sanitize(Map.of("metric", "rowCount", "mustBeBetween", List.of(100, 10)))).isNull();
        assertThat(QualityRules.sanitize(Map.of("metric", "rowCount", "mustBeGreaterThan", "lots"))).isNull();
    }

    @Test
    void rowCountWithoutOperatorIsRefusedButOthersDefaultToZero() {
        assertThat(QualityRules.sanitize(Map.of("metric", "rowCount"))).isNull();
        assertThat(QualityRules.sanitize(Map.of("metric", "nullValues"))).containsEntry("mustBe", 0);
    }

    @Test
    void invalidValuesNeedsArguments() {
        assertThat(QualityRules.sanitize(Map.of("metric", "invalidValues", "mustBe", 0))).isNull();
        assertThat(QualityRules.sanitize(Map.of("metric", "invalidValues", "mustBe", 0,
                "arguments", Map.of("pattern", "^[A-Z]+$", "junk", 1))))
                .containsEntry("arguments", Map.of("pattern", "^[A-Z]+$"));
    }

    @Test
    void unknownTypeRefused() {
        assertThat(QualityRules.sanitize(Map.of("type", "text", "description", "nice"))).isNull();
    }
}
