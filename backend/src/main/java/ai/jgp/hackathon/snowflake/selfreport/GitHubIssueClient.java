package ai.jgp.hackathon.snowflake.selfreport;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * The two GitHub REST calls the self-report flow makes (ported from Control Center):
 * {@code PUT /repos/{owner}/{repo}/contents/{path}} commits an attachment to an orphan branch,
 * {@code POST /repos/{owner}/{repo}/issues} opens the labelled issue.
 *
 * <p>The token ({@code GITHUB_SELF_REPORT_TOKEN}) needs {@code issues: write} and, for
 * attachments, {@code contents: write}. It is never logged: the exceptions below carry the
 * upstream HTTP status only, never the body or the token. With no token the app still starts and
 * every call fails with {@link GitHubNotConfiguredException} (503 {@code not_configured}).
 */
@Component
public class GitHubIssueClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubIssueClient.class);

    static final int MAX_ATTEMPTS = 3;

    private final RestClient api;
    private final String token;
    private final String webBase;
    private final String owner;
    private final String repo;
    private final String branch;
    private final long backoffMs;

    @Autowired
    public GitHubIssueClient(
            @Value("${contextbuilder.self-report.github.token:}") String token,
            @Value("${contextbuilder.self-report.github.api-base:https://api.github.com}") String apiBase,
            @Value("${contextbuilder.self-report.github.web-base:https://github.com}") String webBase,
            @Value("${contextbuilder.self-report.github.repo-owner:jgperrin}") String owner,
            @Value("${contextbuilder.self-report.github.repo-name:ai.jgp.hackathon.snowflake}") String repo,
            @Value("${contextbuilder.self-report.github.branch:used-for-self-reports-do-not-delete}") String branch) {
        this(token, apiBase, webBase, owner, repo, branch, 300);
    }

    /** Test seam: a stub {@code apiBase} and no backoff between retries. */
    GitHubIssueClient(String token, String apiBase, String webBase, String owner, String repo, String branch,
            long backoffMs) {
        this.token = token;
        this.webBase = webBase;
        this.owner = owner;
        this.repo = repo;
        this.branch = branch;
        this.backoffMs = backoffMs;
        this.api = RestClient.builder().baseUrl(apiBase).build();
        if (!isConfigured()) {
            log.warn("GITHUB_SELF_REPORT_TOKEN is not set: /api/self-report answers 503 until it is");
        }
    }

    public boolean isConfigured() {
        return token != null && !token.isBlank();
    }

    /** Commits one attachment and returns the URL the issue body links or embeds. */
    public String uploadAttachment(String reportId, String filename, byte[] bytes) {
        requireConfigured();
        String path = encodePath(reportId + "/" + filename);
        Map<String, Object> body = Map.of(
                "message", "self-report: " + reportId + "/" + filename,
                "content", Base64.getEncoder().encodeToString(bytes),
                "branch", branch);
        call("upload", () -> api.put()
                .uri("/repos/" + owner + "/" + repo + "/contents/" + path)
                .headers(this::authorize)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity());
        // blob/...?raw=true resolves against the viewer's GitHub session, so it works for a private repo too.
        return webBase + "/" + owner + "/" + repo + "/blob/" + branch + "/" + path + "?raw=true";
    }

    /** Opens the issue and returns its html_url. */
    public String createIssue(String title, String body, List<String> labels) {
        requireConfigured();
        Map<String, Object> payload = Map.of("title", title, "body", body, "labels", labels);
        Map<?, ?> response = call("issue create", () -> api.post()
                .uri("/repos/" + owner + "/" + repo + "/issues")
                .headers(this::authorize)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(Map.class));
        if (response == null || response.get("html_url") == null) {
            throw new GitHubTransportException("GitHub issue response has no html_url");
        }
        return String.valueOf(response.get("html_url"));
    }

    private void authorize(HttpHeaders headers) {
        headers.setBearerAuth(token);
        headers.set(HttpHeaders.ACCEPT, "application/vnd.github+json");
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new GitHubNotConfiguredException("GITHUB_SELF_REPORT_TOKEN is not set");
        }
    }

    /** Runs the call with bounded retries on transient failures, then maps what is left to our four types. */
    private <T> T call(String op, Supplier<T> call) {
        try {
            return withRetry(op, call);
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new GitHubAuthException("GitHub refused the configured token (HTTP 401)");
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 429) {
                throw new GitHubTransportException("GitHub " + op + " rate-limited (HTTP 429)");
            }
            throw new GitHubRejectedException("GitHub rejected the " + op + " (HTTP " + e.getStatusCode().value() + ")");
        } catch (HttpServerErrorException e) {
            throw new GitHubTransportException("GitHub " + op + " failed (HTTP " + e.getStatusCode().value() + ")");
        } catch (ResourceAccessException e) {
            throw new GitHubTransportException("GitHub " + op + " failed: network error");
        } catch (GitHubTransportException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new GitHubTransportException("GitHub " + op + " failed: " + e.getClass().getSimpleName());
        }
    }

    private <T> T withRetry(String op, Supplier<T> call) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return call.get();
            } catch (RuntimeException e) {
                if (!isRetryable(e)) {
                    throw e;
                }
                last = e;
                log.warn("GitHub {} transient failure (attempt {}/{}): {}", op, attempt, MAX_ATTEMPTS,
                        e.getClass().getSimpleName());
                if (attempt < MAX_ATTEMPTS && backoffMs > 0) {
                    try {
                        Thread.sleep(backoffMs * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            }
        }
        throw last;
    }

    private static boolean isRetryable(RuntimeException e) {
        if (e instanceof ResourceAccessException || e instanceof HttpServerErrorException) {
            return true;
        }
        return e instanceof HttpClientErrorException c && c.getStatusCode().value() == 429;
    }

    private static String encodePath(String path) {
        StringBuilder out = new StringBuilder();
        String[] parts = path.split("/", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                out.append('/');
            }
            out.append(URLEncoder.encode(parts[i], StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return out.toString();
    }

    /** No token configured: 503 {@code not_configured}. */
    public static class GitHubNotConfiguredException extends RuntimeException {
        GitHubNotConfiguredException(String message) {
            super(message);
        }
    }

    /** GitHub refused the token: 503 {@code github_auth}. */
    public static class GitHubAuthException extends RuntimeException {
        GitHubAuthException(String message) {
            super(message);
        }
    }

    /** GitHub unreachable, 5xx or rate-limited after retries: 502 {@code github_unreachable}. */
    public static class GitHubTransportException extends RuntimeException {
        GitHubTransportException(String message) {
            super(message);
        }
    }

    /** GitHub answered with a non-auth 4xx (404 repo, 422 validation): 502 {@code github_rejected}. */
    public static class GitHubRejectedException extends RuntimeException {
        GitHubRejectedException(String message) {
            super(message);
        }
    }
}
