package ai.jgp.hackathon.snowflake.cortex;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local")
class CortexClientSelectionTest {

    @Autowired
    CortexClient client;

    @Test
    void localProfileWiresTheStubClient() {
        assertThat(client).isInstanceOf(StubCortexClient.class);
        assertThat(client.complete("ping")).isEqualTo(
                "Stubbed answer (no Snowflake connection). You asked: ping");
    }
}
