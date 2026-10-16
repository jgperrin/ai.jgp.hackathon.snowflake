package ai.jgp.hackathon.snowflake.selfreport;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * In-memory sliding one-hour window per client IP. Everyone is limited the same way, signed in or
 * not: sign-in here is one shared demo account whose password is public, so it proves nothing.
 * The map resets on restart, which is fine for a soft cap that deters abuse.
 */
@Component
public class SelfReportRateLimiter {

    private static final Duration WINDOW = Duration.ofHours(1);

    private final int perHour;
    private final Map<String, Deque<Instant>> hits = new HashMap<>();

    public SelfReportRateLimiter(@Value("${contextbuilder.self-report.rate-limit.per-hour:5}") int perHour) {
        this.perHour = perHour;
    }

    /** True, and the hit recorded, when the address is under its hourly limit. */
    public synchronized boolean tryAcquire(String ip) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(WINDOW);
        // Drop addresses whose window has fully passed, so the map cannot grow without bound.
        hits.values().removeIf(q -> !q.isEmpty() && q.peekLast().isBefore(cutoff));
        Deque<Instant> queue = hits.computeIfAbsent(ip == null ? "unknown" : ip, k -> new ArrayDeque<>());
        while (!queue.isEmpty() && queue.peekFirst().isBefore(cutoff)) {
            queue.pollFirst();
        }
        if (queue.size() >= perHour) {
            return false;
        }
        queue.addLast(now);
        return true;
    }
}
