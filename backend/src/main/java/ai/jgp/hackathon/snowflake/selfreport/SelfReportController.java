package ai.jgp.hackathon.snowflake.selfreport;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import ai.jgp.hackathon.snowflake.auth.UserContext;
import jakarta.servlet.http.HttpServletRequest;

/**
 * {@code POST /api/self-report}: the in-app Help panel files a GitHub issue. Behind the sign-in
 * gate like every other /api call, so the caller is {@link UserContext#current()}.
 *
 * <p>Errors are {@code {message, reason}} with a stable reason the panel maps to copy:
 * {@code invalid_request} (400), {@code rate_limited} (429), {@code github_unreachable} /
 * {@code github_rejected} (502), {@code not_configured} / {@code github_auth} (503).
 */
@RestController
public class SelfReportController {

    private final SelfReportService service;
    private final SelfReportRateLimiter limiter;

    public SelfReportController(SelfReportService service, SelfReportRateLimiter limiter) {
        this.service = service;
        this.limiter = limiter;
    }

    @PostMapping("/api/self-report")
    public ResponseEntity<Map<String, Object>> submit(
            @RequestParam("description") String description,
            @RequestParam(value = "context", required = false) String context,
            @RequestParam(value = "attachment", required = false) MultipartFile[] attachments,
            HttpServletRequest request) {
        String ip = clientIp(request);
        if (!limiter.tryAcquire(ip)) {
            return error(HttpStatus.TOO_MANY_REQUESTS, "rate_limited",
                    "Too many reports from your network in the last hour. Try again later.");
        }
        List<MultipartFile> files = attachments == null ? List.of()
                : Arrays.stream(attachments).filter(f -> f != null && f.getOriginalFilename() != null
                        && !f.getOriginalFilename().isEmpty()).toList();
        try {
            SelfReportService.Result result = service.submit(
                    new SelfReportService.SubmitInput(description, context, files, ip, UserContext.current()));
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("issueUrl", result.issueUrl(), "reportId", result.reportId()));
        } catch (GitHubIssueClient.GitHubNotConfiguredException e) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, "not_configured",
                    "Problem reporting is not configured on this server.");
        } catch (GitHubIssueClient.GitHubAuthException e) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, "github_auth",
                    "Problem reporting is misconfigured on this server. Your report was logged for the team.");
        } catch (GitHubIssueClient.GitHubTransportException e) {
            return error(HttpStatus.BAD_GATEWAY, "github_unreachable",
                    "GitHub could not be reached. Your report was logged and the team can recover it.");
        } catch (GitHubIssueClient.GitHubRejectedException e) {
            return error(HttpStatus.BAD_GATEWAY, "github_rejected",
                    "GitHub rejected the report. Your report was logged and the team can recover it.");
        } catch (ResponseStatusException e) {
            return error(HttpStatus.valueOf(e.getStatusCode().value()), "invalid_request", e.getReason());
        }
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String reason, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message == null ? "" : message);
        body.put("reason", reason);
        return ResponseEntity.status(status).body(body);
    }

    /**
     * nginx sets X-Real-IP to the connecting address; X-Forwarded-For is not used because its
     * first hop is whatever the client claims. The app binds loopback only, so nginx is the only
     * way in and the header cannot be forged from outside.
     */
    static String clientIp(HttpServletRequest request) {
        String real = request.getHeader("X-Real-IP");
        return real != null && !real.isBlank() ? real.trim() : request.getRemoteAddr();
    }
}
