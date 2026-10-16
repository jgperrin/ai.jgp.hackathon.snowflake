package ai.jgp.hackathon.snowflake.cortex;

/**
 * The single seam between the application and Cortex. Implementations are selected by
 * Spring profile: JdbcCortexClient talks to a real Snowflake account, StubCortexClient
 * returns canned text so the app can be demoed with no account.
 */
public interface CortexClient {

    String complete(String prompt);
}
