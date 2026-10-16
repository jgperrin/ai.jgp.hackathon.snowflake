package ai.jgp.hackathon.snowflake.selfreport;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Validates a self-report, uploads its attachments, and files the GitHub issue (ported from
 * Control Center). Like Control Center, an undeliverable report is logged at ERROR with its full
 * recoverable payload rather than persisted: this app has no diagnostics store.
 *
 * <p>No credential reaches GitHub or the log. The browser builds its context without any (the
 * Snowflake connection is sent as a boolean only), and {@link #sanitizeContext} drops any key that
 * looks like a secret or a connection setting all the same, so a careless client cannot leak one.
 * The session cookie is never read here, and the signed-in user comes from the server, not the
 * client.
 */
@Service
public class SelfReportService {

    private static final Logger log = LoggerFactory.getLogger(SelfReportService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    static final int MAX_DESCRIPTION_LENGTH = 2000;
    static final int MAX_FILES = 5;
    static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    static final long MAX_TOTAL_BYTES = 25L * 1024 * 1024;
    static final int MAX_TITLE_LENGTH = 60;
    static final Set<String> ALLOWED_MIME_EXACT = Set.of("application/pdf", "text/plain");

    /** Context keys dropped when the name contains any of these (case-insensitive). */
    static final List<String> SECRET_KEY_PARTS = List.of("password", "passwd", "secret", "token", "cookie",
            "authorization", "credential", "privatekey", "private_key", "session");
    /** Context keys dropped when the name is exactly one of these: Snowflake connection settings. */
    static final Set<String> CONNECTION_KEYS = Set.of("connection", "account", "warehouse", "role", "database",
            "schema", "snowflakeuser");

    private final GitHubIssueClient github;
    private final List<String> labels;

    public SelfReportService(GitHubIssueClient github,
            @Value("${contextbuilder.self-report.github.labels:self-report}") String labelsCsv) {
        this.github = github;
        this.labels = Arrays.stream(labelsCsv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public Result submit(SubmitInput input) {
        validate(input);
        String reportId = randomReportId();
        String context = sanitizeContext(input.contextJson());
        try {
            List<Uploaded> uploaded = new ArrayList<>();
            for (MultipartFile file : input.files()) {
                String filename = sanitizeFilename(nameOf(file));
                String url = github.uploadAttachment(reportId, filename, bytesOf(file));
                uploaded.add(new Uploaded(filename, file.getContentType(), url));
            }
            String issueUrl = github.createIssue(buildTitle(input.description()),
                    buildBody(input, context, reportId, uploaded), labels);
            log.info("Self-report {} filed as {} ({} attachment(s))", reportId, issueUrl, uploaded.size());
            return new Result(reportId, issueUrl);
        } catch (GitHubIssueClient.GitHubTransportException | GitHubIssueClient.GitHubRejectedException
                | GitHubIssueClient.GitHubAuthException e) {
            // The report did not land: log everything needed to recover it by hand, then let the
            // controller answer. Attachment bytes are not logged; their names are.
            List<String> names = input.files().stream().map(f -> sanitizeFilename(nameOf(f))).toList();
            log.error("SELF_REPORT_FAILED reportId={} user={} ip={} attachments={} error=\"{}\" description=\"{}\" "
                    + "context={}", reportId, input.user(), input.callerIp(), names, e.getMessage(),
                    input.description(), context);
            throw e;
        }
    }

    void validate(SubmitInput input) {
        String description = input.description();
        if (description == null || description.isBlank()) {
            throw invalid("A description is required");
        }
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw invalid("The description must be " + MAX_DESCRIPTION_LENGTH + " characters or fewer");
        }
        if (input.files().size() > MAX_FILES) {
            throw invalid("At most " + MAX_FILES + " attachments are allowed");
        }
        long total = 0;
        for (MultipartFile file : input.files()) {
            if (!isAllowedMime(file.getContentType())) {
                throw invalid("Attachment type not allowed: " + file.getContentType()
                        + " (images, PDF and plain text only)");
            }
            if (file.isEmpty()) {
                throw invalid("Attachment is empty: " + sanitizeFilename(nameOf(file)));
            }
            if (file.getSize() > MAX_FILE_BYTES) {
                throw invalid("Each attachment must be 10 MB or less: " + sanitizeFilename(nameOf(file)));
            }
            total += file.getSize();
            if (total > MAX_TOTAL_BYTES) {
                throw invalid("Attachments must total 25 MB or less");
            }
        }
    }

    static boolean isAllowedMime(String mime) {
        return mime != null && (ALLOWED_MIME_EXACT.contains(mime) || mime.startsWith("image/"));
    }

    static String buildTitle(String description) {
        String first = description.strip().split("\\R", 2)[0];
        if (first.length() > MAX_TITLE_LENGTH) {
            first = first.substring(0, MAX_TITLE_LENGTH);
        }
        return "[Self-report] " + first;
    }

    static String buildBody(SubmitInput input, String context, String reportId, List<Uploaded> uploaded) {
        StringBuilder sb = new StringBuilder();
        sb.append(input.description().strip()).append("\n\n");
        if (!uploaded.isEmpty()) {
            sb.append("---\n\n### Attachments\n\n");
            for (Uploaded att : uploaded) {
                boolean image = att.contentType() != null && att.contentType().startsWith("image/");
                sb.append(image ? "![" : "- [").append(att.filename()).append("](").append(att.url()).append(")\n");
            }
            sb.append("\n");
        }
        sb.append("---\n\n### Session context\n\n");
        sb.append("Report ID: `").append(reportId).append("`\n\n");
        sb.append("Signed-in user: `").append(input.user() == null ? "unknown" : input.user()).append("`\n\n");
        if (input.callerIp() != null) {
            sb.append("Caller IP: `").append(input.callerIp()).append("`\n\n");
        }
        sb.append("```json\n").append(context).append("\n```\n");
        return sb.toString();
    }

    /**
     * The client context as pretty JSON, with secret-looking keys and connection settings removed
     * at any depth. Anything that is not a JSON object is dropped whole rather than echoed.
     */
    static String sanitizeContext(String contextJson) {
        if (contextJson == null || contextJson.isBlank()) {
            return "{}";
        }
        try {
            JsonNode node = JSON.readTree(contextJson);
            if (node == null || !node.isObject()) {
                return "{\"note\":\"context was not a JSON object and was dropped\"}";
            }
            scrub(node);
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (RuntimeException e) {
            return "{\"note\":\"context was not valid JSON and was dropped\"}";
        }
    }

    private static void scrub(JsonNode node) {
        if (node instanceof ObjectNode object) {
            List<String> names = new ArrayList<>();
            object.properties().forEach(entry -> names.add(entry.getKey()));
            for (String name : names) {
                if (isSensitiveKey(name)) {
                    object.remove(name);
                } else {
                    scrub(object.get(name));
                }
            }
        } else if (node != null && node.isArray()) {
            for (JsonNode child : node) {
                scrub(child);
            }
        }
    }

    static boolean isSensitiveKey(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        return CONNECTION_KEYS.contains(key) || SECRET_KEY_PARTS.stream().anyMatch(key::contains);
    }

    static String sanitizeFilename(String name) {
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        String cleaned = (slash >= 0 ? name.substring(slash + 1) : name).replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.isBlank() ? "attachment" : cleaned;
    }

    private static String nameOf(MultipartFile file) {
        String name = file.getOriginalFilename();
        return name == null || name.isBlank() ? "attachment" : name;
    }

    private static byte[] bytesOf(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw invalid("Could not read attachment: " + sanitizeFilename(nameOf(file)));
        }
    }

    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static String randomReportId() {
        byte[] bytes = new byte[4];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * @param user the signed-in user, from the server's request context, never from the client
     */
    public record SubmitInput(String description, String contextJson, List<MultipartFile> files, String callerIp,
            String user) {
        public SubmitInput {
            files = files == null ? List.of() : files;
        }
    }

    public record Result(String reportId, String issueUrl) {
    }

    record Uploaded(String filename, String contentType, String url) {
    }
}
