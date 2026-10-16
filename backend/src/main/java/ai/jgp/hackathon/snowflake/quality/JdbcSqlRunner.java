package ai.jgp.hackathon.snowflake.quality;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!local")
class JdbcSqlRunner implements SqlRunner {

    private final JdbcTemplate jdbc;

    JdbcSqlRunner(JdbcTemplate jdbc, @Value("${quality.timeout-seconds:60}") int timeoutSeconds) {
        // A copy, so the timeout applies to quality rules only and not to Cortex calls.
        this.jdbc = new JdbcTemplate(jdbc.getDataSource());
        this.jdbc.setQueryTimeout(timeoutSeconds);
        this.jdbc.setMaxRows(1);
    }

    @Override
    public Double scalar(String sql) {
        Number n = jdbc.queryForObject(sql, Number.class);
        return n == null ? null : n.doubleValue();
    }
}
