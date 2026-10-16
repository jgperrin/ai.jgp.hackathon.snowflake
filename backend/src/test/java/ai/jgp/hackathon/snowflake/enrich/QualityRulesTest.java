package ai.jgp.hackathon.snowflake.enrich;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QualityRulesTest {

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
