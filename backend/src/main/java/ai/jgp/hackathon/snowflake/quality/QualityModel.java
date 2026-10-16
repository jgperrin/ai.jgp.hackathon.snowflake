package ai.jgp.hackathon.snowflake.quality;

import java.util.List;

public final class QualityModel {

    private QualityModel() {
    }

    public record RuleResult(String contractId, String table, String column, String rule, String sql,
            boolean passed, Double observed, String threshold, String error) {
    }

    public record RunResult(List<RuleResult> results, int passed, int failed) {
    }
}
