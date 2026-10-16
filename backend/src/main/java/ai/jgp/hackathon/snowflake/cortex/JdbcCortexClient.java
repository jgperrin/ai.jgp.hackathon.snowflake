package ai.jgp.hackathon.snowflake.cortex;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import ai.jgp.hackathon.snowflake.config.CortexProperties;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

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
        // The default output limit truncates long JSON answers mid-object; ask for room explicitly.
        String answer = jdbc.queryForObject(
                "SELECT AI_COMPLETE(?, ?, {'max_tokens': 8192, 'temperature': 0}) AS answer", String.class, model,
                prompt);
        if (answer == null) {
            throw new IllegalStateException("Cortex returned no answer for model " + model);
        }
        return text(unquote(answer));
    }

    /**
     * With model parameters, some Cortex versions wrap the text in {"choices": [{"messages": "..."}]}.
     * Unwrap that shape; anything else is returned untouched.
     */
    static String text(String answer) {
        String a = answer.strip();
        if (!a.startsWith("{") || !a.contains("\"choices\"")) {
            return answer;
        }
        try {
            JsonNode choice = JSON.readTree(a).path("choices").path(0);
            JsonNode text = choice.has("messages") ? choice.get("messages") : choice.path("message").path("content");
            return text.isString() ? text.asString() : answer;
        } catch (JacksonException e) {
            return answer;
        }
    }

    /** AI_COMPLETE can hand back its text as a JSON string literal ("\"...\""); unwrap it once. */
    static String unquote(String answer) {
        String a = answer.strip();
        if (a.length() >= 2 && a.startsWith("\"") && a.endsWith("\"")) {
            try {
                return JSON.readValue(a, String.class);
            } catch (JacksonException e) {
                return answer;
            }
        }
        return answer;
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();
}
