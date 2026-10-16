package ai.jgp.hackathon.snowflake.enrich;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import ai.jgp.hackathon.snowflake.auth.UserContext;
import ai.jgp.hackathon.snowflake.cortex.CortexClient;
import ai.jgp.hackathon.snowflake.product.Bundle;
import ai.jgp.hackathon.snowflake.product.StandardsValidator;

class EnrichJobsTest {

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    static EnrichJobs.JobView waitFor(EnrichJobs jobs, String id) throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            EnrichJobs.JobView v = jobs.get(id);
            if (v.status() != EnrichJobs.Status.RUNNING) {
                return v;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("job did not finish");
    }

    @Test
    void runsInTheBackgroundAsTheCallerAndOnlyTheCallerCanRead() throws Exception {
        AtomicReference<String> seenBy = new AtomicReference<>();
        CortexClient cortex = prompt -> {
            seenBy.set(UserContext.current());
            return "{\"table\": {\"description\": \"A table.\"}}";
        };
        EnrichJobs jobs = new EnrichJobs(new EnrichService(EnrichServiceTest.PROFILE, cortex, new StandardsValidator()));
        UserContext.set("alice");
        EnrichJobs.JobView started = jobs.start(EnrichServiceTest.BUNDLE);
        assertThat(started.id()).isNotBlank();
        EnrichJobs.JobView done = waitFor(jobs, started.id());
        assertThat(done.status()).isEqualTo(EnrichJobs.Status.DONE);
        assertThat(done.result().proposals()).hasSize(1);
        assertThat(seenBy.get()).isEqualTo("alice");

        UserContext.set("bob");
        assertThatThrownBy(() -> jobs.get(started.id())).isInstanceOf(EnrichJobs.UnknownJobException.class);
        assertThatThrownBy(() -> jobs.get("nope")).isInstanceOf(EnrichJobs.UnknownJobException.class);
        jobs.destroy();
    }

    @Test
    void failureIsReportedNotThrown() throws Exception {
        ai.jgp.hackathon.snowflake.profile.ProfileService broken = request -> {
            throw new IllegalStateException("warehouse suspended");
        };
        EnrichJobs jobs = new EnrichJobs(new EnrichService(broken, prompt -> "", new StandardsValidator()));
        UserContext.set("alice");
        EnrichJobs.JobView done = waitFor(jobs, jobs.start(EnrichServiceTest.BUNDLE).id());
        assertThat(done.status()).isEqualTo(EnrichJobs.Status.FAILED);
        assertThat(done.error()).contains("warehouse suspended");
        jobs.destroy();
    }

    @Test
    void malformedBundleIsRefusedUpFront() {
        EnrichJobs jobs = new EnrichJobs(new EnrichService(EnrichServiceTest.PROFILE, p -> "", new StandardsValidator()));
        assertThatThrownBy(() -> jobs.start(new Bundle(Map.of(), null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> jobs.start(new Bundle(Map.of(), List.of(), null)))
                .isInstanceOf(IllegalArgumentException.class);
        jobs.destroy();
    }
}
