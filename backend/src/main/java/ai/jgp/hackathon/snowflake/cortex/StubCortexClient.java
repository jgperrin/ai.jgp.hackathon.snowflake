package ai.jgp.hackathon.snowflake.cortex;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Active under the 'local' profile: no Snowflake account, no network, no credits spent.
 * Exists so the UI can be built and demoed before credentials are wired up.
 */
@Component
@Profile("local")
class StubCortexClient implements CortexClient {

    @Override
    public String complete(String prompt) {
        return "Stubbed answer (no Snowflake connection). You asked: " + prompt;
    }
}
