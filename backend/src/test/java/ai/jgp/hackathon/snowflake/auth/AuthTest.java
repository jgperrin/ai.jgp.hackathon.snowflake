package ai.jgp.hackathon.snowflake.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AuthTest {

    @Autowired
    MockMvc mockMvc;

    MockHttpSession signIn() throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"jgp\",\"password\":\"odcs\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("jgp"))
                .andReturn().getRequest().getSession(false);
    }

    @Test
    void demoCredentialsArePublic() throws Exception {
        mockMvc.perform(get("/api/auth/demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("jgp"))
                .andExpect(jsonPath("$.password").value("odcs"));
    }

    @Test
    void apiNeedsSignIn() throws Exception {
        mockMvc.perform(get("/api/catalog/databases"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Sign in first"));
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordIsRefused() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"jgp\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signInThenUseThenSignOut() throws Exception {
        MockHttpSession session = signIn();
        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.username").value("jgp"));
        mockMvc.perform(get("/api/catalog/databases").session(session)).andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/catalog/databases").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void passwordIsNeverReturned() throws Exception {
        MockHttpSession session = signIn();
        String body = mockMvc.perform(put("/api/me/connection").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"acme-lab\",\"user\":\"BOB\",\"password\":\"s3cret-Pw\","
                                + "\"warehouse\":\"WH\",\"database\":\"DB\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.passwordSet").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("s3cret-Pw");
        String view = mockMvc.perform(get("/api/me/connection").session(session))
                .andExpect(jsonPath("$.account").value("acme-lab"))
                .andReturn().getResponse().getContentAsString();
        assertThat(view).doesNotContain("s3cret-Pw").doesNotContain("password\"");
    }

    @Test
    void contextIsCarriedToWorkerThreadsOnlyWhenWrapped() throws Exception {
        UserContext.set("alice");
        try {
            assertThat(java.util.concurrent.CompletableFuture.supplyAsync(UserContext.wrap(UserContext::current))
                    .get()).isEqualTo("alice");
            assertThat(java.util.concurrent.CompletableFuture.supplyAsync(UserContext::current).get()).isNull();
        } finally {
            UserContext.clear();
        }
    }

    @Test
    void signInSurvivesALostSession() throws Exception {
        jakarta.servlet.http.Cookie cookie = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"jgp\",\"password\":\"odcs\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("cb_signin");
        assertThat(cookie).isNotNull();
        // No session at all, as after a backend restart: the cookie alone signs the user back in.
        mockMvc.perform(get("/api/catalog/databases").cookie(cookie)).andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me").cookie(cookie)).andExpect(jsonPath("$.username").value("jgp"));
        mockMvc.perform(get("/api/catalog/databases").cookie(new jakarta.servlet.http.Cookie("cb_signin", "forged")))
                .andExpect(status().isUnauthorized());
        jakarta.servlet.http.Cookie cleared = mockMvc.perform(post("/api/auth/logout").cookie(cookie))
                .andReturn().getResponse().getCookie("cb_signin");
        assertThat(cleared.getMaxAge()).isZero();
    }
}
