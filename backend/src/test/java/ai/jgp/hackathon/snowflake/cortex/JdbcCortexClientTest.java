package ai.jgp.hackathon.snowflake.cortex;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JdbcCortexClientTest {

    @Test
    void unwrapsJsonStringLiterals() {
        assertThat(JdbcCortexClient.unquote("\"OK\"")).isEqualTo("OK");
        assertThat(JdbcCortexClient.unquote("\"{\\\"table\\\": {\\\"description\\\": \\\"x\\\"}}\\n\""))
                .isEqualTo("{\"table\": {\"description\": \"x\"}}\n");
    }

    @Test
    void leavesPlainTextAlone() {
        assertThat(JdbcCortexClient.unquote("{\"a\": 1}")).isEqualTo("{\"a\": 1}");
        assertThat(JdbcCortexClient.unquote("\"broken")).isEqualTo("\"broken");
        assertThat(JdbcCortexClient.unquote("\"not \"valid\" json\"")).isEqualTo("\"not \"valid\" json\"");
    }
}
