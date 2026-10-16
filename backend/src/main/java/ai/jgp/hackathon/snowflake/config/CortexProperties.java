package ai.jgp.hackathon.snowflake.config;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Snowflake connection settings, bound from the 'snowflake.*' properties.
 *
 * @param account   full account identifier, e.g. "myorg-myaccount" or "xy12345.us-east-1".
 *                  The ".snowflakecomputing.com" suffix is appended here.
 * @param model     Cortex model passed to AI_COMPLETE. There is no server-side default,
 *                  so one is always sent explicitly.
 */
@ConfigurationProperties("snowflake")
public record CortexProperties(
        String account,
        String user,
        String password,
        String warehouse,
        String database,
        String schema,
        String role,
        @DefaultValue("claude-sonnet-5") String model) {

    public String jdbcUrl() {
        StringBuilder url = new StringBuilder("jdbc:snowflake://")
                .append(account)
                .append(".snowflakecomputing.com/");
        StringBuilder query = new StringBuilder();
        appendParam(query, "db", database);
        appendParam(query, "schema", schema);
        appendParam(query, "warehouse", warehouse);
        appendParam(query, "role", role);
        return query.isEmpty() ? url.toString() : url.append('?').append(query).toString();
    }

    private static void appendParam(StringBuilder query, String name, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!query.isEmpty()) {
            query.append('&');
        }
        query.append(name).append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8));
    }
}
