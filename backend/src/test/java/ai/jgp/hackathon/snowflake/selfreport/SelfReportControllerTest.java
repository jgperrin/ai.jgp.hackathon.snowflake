package ai.jgp.hackathon.snowflake.selfreport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import ai.jgp.hackathon.snowflake.auth.UserContext;

/**
 * The app context runs without a GitHub token (as local dev and a bare JAR do); the GitHub-facing
 * cases build the controller against {@link StubGitHub}, never the real API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class SelfReportControllerTest {

    @Autowired
    MockMvc app;

    StubGitHub stub;

    @BeforeEach
    void setUp() throws Exception {
        stub = new StubGitHub();
        UserContext.set("jgp");
    }

    @AfterEach
    void tearDown() {
        stub.close();
        UserContext.clear();
    }

    MockMvc against(String token, int perHour) {
        GitHubIssueClient client = new GitHubIssueClient(token, stub.apiBase(), "https://github.com", "jgperrin",
                "ai.jgp.hackathon.snowflake", "used-for-self-reports-do-not-delete", 0);
        return MockMvcBuilders.standaloneSetup(new SelfReportController(
                new SelfReportService(client, "self-report"), new SelfReportRateLimiter(perHour))).build();
    }

    static MockMultipartHttpServletRequestBuilder report(String ip) {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/self-report");
        request.param("description", "The analyze step spins forever")
                .param("context", "{\"appVersion\":\"0.10.19\",\"step\":\"analyze\"}")
                .with(r -> {
                    r.setRemoteAddr(ip);
                    return r;
                });
        return request;
    }

    @Test
    void needsSignIn() throws Exception {
        UserContext.clear();
        app.perform(report("198.51.100.1")).andExpect(status().isUnauthorized());
    }

    @Test
    void appStartsWithoutATokenAndAnswers503NotConfigured() throws Exception {
        UserContext.clear();
        MockHttpSession session = (MockHttpSession) app.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"jgp\",\"password\":\"odcs\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
        app.perform(report("198.51.100.2").session(session))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.reason").value("not_configured"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void filesTheIssueAndAnswers201WithItsUrl() throws Exception {
        against("ghp_test", 5).perform(report("198.51.100.3")
                        .file(new MockMultipartFile("attachment", "shot.png", "image/png", new byte[] {1, 2, 3})))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.issueUrl").value(StubGitHub.ISSUE_URL))
                .andExpect(jsonPath("$.reportId").isNotEmpty());
        assertThat(stub.all()).contains("Signed-in user: `jgp`").contains("198.51.100.3");
    }

    @Test
    void theSixthReportFromOneIpInTheHourIs429() throws Exception {
        MockMvc mvc = against("ghp_test", 5);
        for (int i = 0; i < 5; i++) {
            mvc.perform(report("198.51.100.4")).andExpect(status().isCreated());
        }
        mvc.perform(report("198.51.100.4"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.reason").value("rate_limited"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Try again later")));
        // Another address still gets through.
        mvc.perform(report("198.51.100.5")).andExpect(status().isCreated());
    }

    @Test
    void aGitHubRejectionIs502GithubRejected() throws Exception {
        stub.issueStatus = 422;
        against("ghp_test", 5).perform(report("198.51.100.6"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.reason").value("github_rejected"));
    }

    @Test
    void anUnreachableGitHubIs502GithubUnreachable() throws Exception {
        MockMvc mvc = against("ghp_test", 5);
        stub.close();
        mvc.perform(report("198.51.100.7"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.reason").value("github_unreachable"));
    }

    @Test
    void anInvalidReportIs400InvalidRequest() throws Exception {
        against("ghp_test", 5).perform(report("198.51.100.8")
                        .file(new MockMultipartFile("attachment", "run.sh", "application/x-sh", new byte[] {1})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("invalid_request"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("not allowed")));
        assertThat(stub.requests).isEmpty();
    }
}
