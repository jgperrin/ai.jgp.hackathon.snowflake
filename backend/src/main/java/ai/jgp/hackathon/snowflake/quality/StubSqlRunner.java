package ai.jgp.hackathon.snowflake.quality;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Offline: row counts look real, violation counts are zero except for a deliberate failure. */
@Component
@Profile("local")
class StubSqlRunner implements SqlRunner {

    @Override
    public Double scalar(String sql) {
        if (sql.startsWith("SELECT COUNT(*) FROM") && !sql.contains("WHERE")) {
            return 14_000d;
        }
        return sql.contains("CLAIM_STATUS") ? 12d : 0d;
    }
}
