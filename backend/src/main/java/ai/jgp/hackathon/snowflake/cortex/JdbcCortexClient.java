package ai.jgp.hackathon.snowflake.cortex;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import ai.jgp.hackathon.snowflake.config.CortexProperties;

/**
 * Calls Cortex over JDBC. AI_COMPLETE is the current SQL surface; the older
 * SNOWFLAKE.CORTEX.COMPLETE is legacy and leaves the back-compat window during 2026.
 */
@Component
@Profile("!local")
class JdbcCortexClient implements CortexClient {

    private final JdbcTemplate jdbc;
    private final String model;

    JdbcCortexClient(JdbcTemplate jdbc, CortexProperties properties) {
        this.jdbc = jdbc;
        this.model = properties.model();
    }

    @Override
    public String complete(String prompt) {
        String answer = jdbc.queryForObject(
                "SELECT AI_COMPLETE(?, ?) AS answer", String.class, model, prompt);
        if (answer == null) {
            throw new IllegalStateException("Cortex returned no answer for model " + model);
        }
        return answer;
    }
}
