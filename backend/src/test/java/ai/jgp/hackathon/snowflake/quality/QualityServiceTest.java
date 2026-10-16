package ai.jgp.hackathon.snowflake.quality;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;

import ai.jgp.hackathon.snowflake.product.Bundle;
import ai.jgp.hackathon.snowflake.quality.QualityModel.RunResult;

class QualityServiceTest {

    static Bundle bundle() {
        Map<String, Object> contract = Map.of("id", "c1",
                "servers", List.of(Map.of("database", "DB", "schema", "S")),
                "schema", List.of(Map.of("name", "CLAIMS",
                        "quality", List.of(
                                Map.of("type", "sql", "query", "SELECT COUNT(*) FROM ${table} WHERE BROKEN", "mustBe", 0),
                                Map.of("type", "sql", "query", "SELECT COUNT(*) FROM ${table} WHERE B < A", "mustBe", 0,
                                        "description", "B after A")),
                        "properties", List.of(Map.of("name", "ID",
                                "quality", List.of(Map.of("metric", "duplicateValues", "mustBe", 0),
                                        Map.of("metric", "rowCount", "mustBeGreaterThan", 100)))))));
        return new Bundle(Map.of(), List.of(contract), null);
    }

    @Test
    void oneBrokenRuleDoesNotStopTheRun() {
        SqlRunner runner = sql -> {
            if (sql.contains("BROKEN")) {
                throw new BadSqlGrammarException("rule", sql, new java.sql.SQLException("invalid identifier 'BROKEN'"));
            }
            return sql.contains("DISTINCT") ? 3d : sql.contains("WHERE") ? 0d : 50d;
        };
        RunResult r = new QualityService(runner).run(bundle());
        assertThat(r.results()).hasSize(4);
        assertThat(r.passed()).isEqualTo(1);
        assertThat(r.failed()).isEqualTo(3);
        assertThat(r.results().get(0).error()).contains("invalid identifier");
        assertThat(r.results().get(1).passed()).isTrue();
        assertThat(r.results().get(1).rule()).isEqualTo("B after A");
        assertThat(r.results().get(2).observed()).isEqualTo(3d);
        assertThat(r.results().get(2).rule()).isEqualTo("duplicateValues on ID");
        assertThat(r.results().get(3).threshold()).isEqualTo("> 100");
        assertThat(r.results().get(3).passed()).isFalse();
    }
}
