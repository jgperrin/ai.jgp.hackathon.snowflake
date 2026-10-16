package ai.jgp.hackathon.snowflake.enrich;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import ai.jgp.hackathon.snowflake.cortex.CortexClient;
import ai.jgp.hackathon.snowflake.product.Bundle;
import ai.jgp.hackathon.snowflake.product.StandardsValidator;
import ai.jgp.hackathon.snowflake.profile.ProfileModel.ColumnProfile;
import ai.jgp.hackathon.snowflake.profile.ProfileModel.SchemaProfile;
import ai.jgp.hackathon.snowflake.profile.ProfileModel.TableProfile;
import ai.jgp.hackathon.snowflake.profile.ProfileService;

class EnrichServiceTest {

    static final Bundle BUNDLE = new Bundle(Map.of(), List.of(Map.of("id", "c1",
            "servers", List.of(Map.of("database", "DB", "schema", "S")),
            "schema", List.of(Map.of("name", "T")))), null);

    static final ProfileService PROFILE = request -> new SchemaProfile("DB", "S", List.of(new TableProfile("T", 10L,
            false, 10, List.of(new ColumnProfile("C", "TEXT", 0, 0, 10, "a", "z", 1, List.of(), List.of("a"))))));

    @Test
    void retriesOnceWhenTheFirstAnswerIsMalformed() {
        AtomicInteger calls = new AtomicInteger();
        CortexClient cortex = prompt -> calls.incrementAndGet() == 1 ? "{\"table\": {\"descr"
                : "{\"table\": {\"description\": \"A table.\"}}";
        EnrichService.EnrichResult r = new EnrichService(PROFILE, cortex, new StandardsValidator()).enrich(BUNDLE);
        assertThat(calls).hasValue(2);
        assertThat(r.proposals()).hasSize(1);
        assertThat(r.warnings()).isEmpty();
    }

    @Test
    void givesUpAfterTwoBadAnswersWithAWarning() {
        AtomicInteger calls = new AtomicInteger();
        CortexClient cortex = prompt -> {
            calls.incrementAndGet();
            return "sorry";
        };
        EnrichService.EnrichResult r = new EnrichService(PROFILE, cortex, new StandardsValidator()).enrich(BUNDLE);
        assertThat(calls).hasValue(2);
        assertThat(r.proposals()).isEmpty();
        assertThat(r.warnings()).hasSize(1);
    }

    @Test
    void cortexFailureBecomesAWarning() {
        CortexClient cortex = prompt -> {
            throw new IllegalStateException("model not available");
        };
        EnrichService.EnrichResult r = new EnrichService(PROFILE, cortex, new StandardsValidator()).enrich(BUNDLE);
        assertThat(r.warnings()).singleElement().asString().contains("model not available");
    }
}
