package ai.jgp.hackathon.snowflake.selfreport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(OutputCaptureExtension.class)
class SelfReportServiceTest {

    static final String PASSWORD = "s3cret-Pw";
    static final String COOKIE = "cb_signin=jgp.1767225600.deadbeefcafe";

    /** What a careless or hostile client could send: none of it may reach GitHub or the log. */
    static final String LEAKY_CONTEXT = "{\"appVersion\":\"0.10.19\",\"step\":\"analyze\",\"route\":\"/contextbuilder/\","
            + "\"snowflakeConnected\":true,\"password\":\"" + PASSWORD + "\",\"cookie\":\"" + COOKIE + "\","
            + "\"connection\":{\"account\":\"acme-lab\",\"user\":\"BOB\",\"warehouse\":\"WH_SECRET\"},"
            + "\"recentErrors\":[{\"status\":502,\"message\":\"Snowflake: boom\",\"sessionToken\":\"tok-123\"}]}";

    StubGitHub stub;
    SelfReportService service;

    @BeforeEach
    void setUp() throws Exception {
        stub = new StubGitHub();
        service = new SelfReportService(client("ghp_test"), "self-report");
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    GitHubIssueClient client(String token) {
        return new GitHubIssueClient(token, stub.apiBase(), "https://github.com", "jgperrin",
                "ai.jgp.hackathon.snowflake", "used-for-self-reports-do-not-delete", 0);
    }

    static SelfReportService.SubmitInput input(String description, String context, List<MultipartFile> files) {
        return new SelfReportService.SubmitInput(description, context, files, "203.0.113.7", "jgp");
    }

    static MockMultipartFile file(String name, String type, int size) {
        return new MockMultipartFile("attachment", name, type, new byte[size]);
    }

    @Test
    void titleIsTheFirstLineCutAtSixtyCharacters() {
        assertThat(SelfReportService.buildTitle("Short one\nsecond line")).isEqualTo("[Self-report] Short one");
        String long70 = "x".repeat(70);
        assertThat(SelfReportService.buildTitle(long70)).isEqualTo("[Self-report] " + "x".repeat(60));
    }

    @Test
    void filesAnIssueLabelledSelfReportWithTheContext() {
        SelfReportService.Result result = service.submit(input("It broke on analyze", LEAKY_CONTEXT,
                List.of(file("shot.png", "image/png", 10))));
        assertThat(result.issueUrl()).isEqualTo(StubGitHub.ISSUE_URL);
        assertThat(result.reportId()).hasSize(8);
        String sent = stub.all();
        assertThat(sent).contains("PUT /repos/jgperrin/ai.jgp.hackathon.snowflake/contents/" + result.reportId()
                + "/shot.png");
        assertThat(sent).contains("POST /repos/jgperrin/ai.jgp.hackathon.snowflake/issues");
        assertThat(sent).contains("[Self-report] It broke on analyze").contains("\"labels\":[\"self-report\"]");
        // The body stands on its own: version, step, route, user, connection flag, recent errors.
        assertThat(sent).contains("0.10.19").contains("analyze").contains("/contextbuilder/")
                .contains("Signed-in user: `jgp`").contains("snowflakeConnected").contains("Snowflake: boom")
                .contains("shot.png?raw=true");
    }

    @Test
    void noCredentialReachesGitHub() {
        service.submit(input("Leak check", LEAKY_CONTEXT, List.of()));
        assertThat(stub.all()).doesNotContain(PASSWORD).doesNotContain(COOKIE).doesNotContain("cb_signin")
                .doesNotContain("acme-lab").doesNotContain("BOB").doesNotContain("WH_SECRET")
                .doesNotContain("tok-123");
    }

    @Test
    void theCallerIpStaysOutOfThePublicIssue() {
        // The repo is public: the IP feeds the rate limit and the ERROR log, never the issue.
        service.submit(input("IP check", LEAKY_CONTEXT, List.of()));
        assertThat(stub.all()).doesNotContain("203.0.113.7");
    }

    @Test
    void aGitHubRejectionIsLoggedAtErrorWithoutCredentials(CapturedOutput output) {
        stub.issueStatus = 422;
        assertThatThrownBy(() -> service.submit(input("Recover me please", LEAKY_CONTEXT, List.of())))
                .isInstanceOf(GitHubIssueClient.GitHubRejectedException.class);
        assertThat(output.getAll()).contains("SELF_REPORT_FAILED").contains("ERROR").contains("Recover me please")
                .contains("analyze");
        assertThat(output.getAll()).doesNotContain(PASSWORD).doesNotContain(COOKIE).doesNotContain("acme-lab")
                .doesNotContain("WH_SECRET").doesNotContain("tok-123").doesNotContain("ghp_test");
    }

    @Test
    void unreachableGitHubIsATransportFailure() {
        stub.close();
        assertThatThrownBy(() -> service.submit(input("Anyone there?", "{}", List.of())))
                .isInstanceOf(GitHubIssueClient.GitHubTransportException.class);
    }

    @Test
    void noTokenMeansNotConfigured() {
        SelfReportService unconfigured = new SelfReportService(client(""), "self-report");
        assertThatThrownBy(() -> unconfigured.submit(input("Hello", "{}", List.of())))
                .isInstanceOf(GitHubIssueClient.GitHubNotConfiguredException.class);
        assertThat(stub.requests).isEmpty();
    }

    @Test
    void validationBounds() {
        assertRefused(input(" ", "{}", List.of()), "required");
        assertRefused(input("x".repeat(2001), "{}", List.of()), "2000");
        assertRefused(input("ok", "{}", Collections.nCopies(6, file("a.png", "image/png", 1))), "5");
        assertRefused(input("ok", "{}", List.of(file("big.png", "image/png", 10 * 1024 * 1024 + 1))), "10 MB");
        assertRefused(input("ok", "{}", List.of(file("run.sh", "application/x-sh", 5))), "not allowed");
        List<MultipartFile> three9mb = Collections.nCopies(3, file("a.pdf", "application/pdf", 9 * 1024 * 1024));
        assertRefused(input("ok", "{}", three9mb), "25 MB");
        assertThat(stub.requests).isEmpty();
        // At the bounds exactly: accepted.
        service.validate(input("x".repeat(2000), "{}", List.of(file("a.txt", "text/plain", 1))));
    }

    @Test
    void anUnreadableContextIsDroppedNotEchoed() {
        assertThat(SelfReportService.sanitizeContext("not json " + PASSWORD)).doesNotContain(PASSWORD);
        assertThat(SelfReportService.sanitizeContext(null)).isEqualTo("{}");
    }

    static void assertRefused(SelfReportService.SubmitInput input, String text) {
        assertThatThrownBy(() -> new SelfReportService(null, "self-report").validate(input))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(text);
    }
}
