package ai.jgp.hackathon.snowflake.enrich;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import ai.jgp.hackathon.snowflake.auth.UserContext;
import ai.jgp.hackathon.snowflake.enrich.EnrichService.EnrichResult;
import ai.jgp.hackathon.snowflake.product.Bundle;

/**
 * Runs the Cortex analysis in the background. A Cortex run takes 30 to 90 s; holding one HTTP
 * request open that long gets it cut by any proxy with a shorter read timeout (a bare 502).
 * The browser starts a job and polls instead, so no request lasts more than a moment.
 */
@Component
public class EnrichJobs implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(EnrichJobs.class);
    static final Duration TTL = Duration.ofMinutes(30);

    public enum Status { RUNNING, DONE, FAILED }

    public record JobView(String id, Status status, long elapsedSeconds, EnrichResult result, String error) {
    }

    private static final class Job {
        final String id = UUID.randomUUID().toString();
        final String owner;
        final Instant started = Instant.now();
        volatile Instant finished;
        volatile Status status = Status.RUNNING;
        volatile EnrichResult result;
        volatile String error;

        Job(String owner) {
            this.owner = owner;
        }

        JobView view() {
            Instant end = finished == null ? Instant.now() : finished;
            return new JobView(id, status, Duration.between(started, end).toSeconds(), result, error);
        }
    }

    private final EnrichService enrich;
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final ExecutorService runner = Executors.newCachedThreadPool();

    EnrichJobs(EnrichService enrich) {
        this.enrich = enrich;
    }

    public JobView start(Bundle bundle) {
        EnrichService.requireContracts(bundle);
        evictExpired();
        Job job = new Job(UserContext.current());
        jobs.put(job.id, job);
        // Captured here, on the request thread: the worker thread has no signed-in user of its own.
        java.util.function.Supplier<Void> task = UserContext.wrap(() -> {
            try {
                job.result = enrich.enrich(bundle);
                job.status = Status.DONE;
            } catch (RuntimeException e) {
                log.warn("Analysis job {} failed", job.id, e);
                job.error = e.getMessage();
                job.status = Status.FAILED;
            } finally {
                job.finished = Instant.now();
            }
            return null;
        });
        runner.submit(task::get);
        return job.view();
    }

    /** Only the user who started a job can see it; anyone else gets the same answer as for an unknown id. */
    public JobView get(String id) {
        Job job = jobs.get(id);
        if (job == null || !java.util.Objects.equals(job.owner, UserContext.current())) {
            throw new UnknownJobException(id);
        }
        return job.view();
    }

    private void evictExpired() {
        Instant cutoff = Instant.now().minus(TTL);
        jobs.values().removeIf(j -> j.started.isBefore(cutoff));
    }

    @Override
    public void destroy() {
        runner.shutdownNow();
    }

    public static class UnknownJobException extends RuntimeException {

        UnknownJobException(String id) {
            super("No analysis job " + id);
        }
    }
}
