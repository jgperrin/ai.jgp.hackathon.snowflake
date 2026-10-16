package ai.jgp.hackathon.snowflake.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RuleCompilerTest {

    static final String T = "\"DB\".\"S\".\"T\"";

    String sql(Map<String, Object> rule, String column) {
        return RuleCompiler.sql(rule, "DB", "S", "T", column);
    }

    @Test
    void libraryMetrics() {
        assertThat(sql(Map.of("metric", "rowCount"), null)).isEqualTo("SELECT COUNT(*) FROM " + T);
        assertThat(sql(Map.of("metric", "nullValues"), "C")).isEqualTo("SELECT COUNT(*) FROM " + T + " WHERE \"C\" IS NULL");
        assertThat(sql(Map.of("metric", "missingValues"), "C")).contains("TRIM(\"C\"::VARCHAR) = ''");
        assertThat(sql(Map.of("metric", "duplicateValues"), "C"))
                .isEqualTo("SELECT COUNT(\"C\") - COUNT(DISTINCT \"C\") FROM " + T);
        assertThat(sql(Map.of("metric", "invalidValues", "arguments", Map.of("validValues", List.of("A", "O'B"))), "C"))
                .endsWith("NOT (\"C\"::VARCHAR IN ('A', 'O''B'))");
        assertThat(sql(Map.of("metric", "invalidValues", "arguments", Map.of("pattern", "^\\d+$")), "C"))
                .endsWith("NOT (REGEXP_LIKE(\"C\"::VARCHAR, '^\\\\d+$'))");
    }

    @Test
    void sqlRulesFillPlaceholdersAndRefuseWrites() {
        assertThat(sql(Map.of("type", "sql", "query", "SELECT COUNT(*) FROM ${table} WHERE ${column} < 0;"), "C"))
                .isEqualTo("SELECT COUNT(*) FROM " + T + " WHERE \"C\" < 0");
        assertThatThrownBy(() -> sql(Map.of("type", "sql", "query", "DELETE FROM ${table}"), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sql(Map.of("type", "sql", "query", "SELECT 1; DROP TABLE x"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void columnMetricsNeedAColumn() {
        assertThatThrownBy(() -> sql(Map.of("metric", "nullValues"), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sql(Map.of("metric", "invalidValues"), "C")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sql(Map.of("metric", "nope"), "C")).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({
        "mustBe,0,0,true", "mustBe,0,3,false", "mustNotBe,0,3,true", "mustBeGreaterThan,10,11,true",
        "mustBeGreaterThan,10,10,false", "mustBeGreaterOrEqualTo,10,10,true", "mustBeLessThan,5,4,true",
        "mustBeLessThan,5,5,false", "mustBeLessOrEqualTo,5,5,true"
    })
    void operators(String op, double threshold, double observed, boolean expected) {
        assertThat(RuleCompiler.passes(Map.of(op, threshold), observed)).isEqualTo(expected);
    }

    @Test
    void ranges() {
        assertThat(RuleCompiler.passes(Map.of("mustBeBetween", List.of(10, 100)), 50)).isTrue();
        assertThat(RuleCompiler.passes(Map.of("mustBeBetween", List.of(10, 100)), 5)).isFalse();
        assertThat(RuleCompiler.passes(Map.of("mustNotBeBetween", List.of(10, 100)), 5)).isTrue();
        assertThat(RuleCompiler.threshold(Map.of("mustBeBetween", List.of(10, 100)))).isEqualTo("between 10 and 100");
        assertThat(RuleCompiler.threshold(Map.of("mustBeLessThan", 5))).isEqualTo("< 5");
        assertThat(RuleCompiler.threshold(Map.of())).isEqualTo("= 0");
        assertThat(RuleCompiler.passes(Map.of(), 0)).isTrue();
    }
}
