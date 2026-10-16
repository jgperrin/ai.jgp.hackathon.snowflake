package ai.jgp.hackathon.snowflake.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

// Sign-in is covered by AuthTest; here every request runs as the demo user. The password below is a fake marker.
class ConnectionControllerTest {

    static final String FAKE_PASSWORD = "fake-pw-must-never-leak";

    @Nested
    @SpringBootTest(properties = { "contextbuilder.auth.required=false", "snowflake.account=acme-lab",
            "snowflake.user=JUDGE", "snowflake.password=" + FAKE_PASSWORD, "snowflake.warehouse=WH",
            "snowflake.database=DB", "snowflake.role=ANALYST" })
    @AutoConfigureMockMvc
    @ActiveProfiles("local")
    class WithServerSettings {

        @Autowired
        MockMvc mockMvc;

        @Test
        void defaultsAreTheServerValuesAndNeverThePassword() throws Exception {
            String body = mockMvc.perform(get("/api/me/connection/defaults"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.account").value("acme-lab"))
                    .andExpect(jsonPath("$.user").value("JUDGE"))
                    .andExpect(jsonPath("$.warehouse").value("WH"))
                    .andExpect(jsonPath("$.database").value("DB"))
                    .andExpect(jsonPath("$.role").value("ANALYST"))
                    .andExpect(jsonPath("$.password").doesNotExist())
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain(FAKE_PASSWORD).doesNotContain("password");
        }

        @Test
        void seededProfileReadsAsSavedAndNeverCarriesThePassword() throws Exception {
            String body = mockMvc.perform(get("/api/me/connection"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.state").value("SAVED"))
                    .andExpect(jsonPath("$.passwordSet").value(true))
                    .andExpect(jsonPath("$.account").value("acme-lab"))
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain(FAKE_PASSWORD);
        }
    }

    @Nested
    @SpringBootTest(properties = { "contextbuilder.auth.required=false", "snowflake.account=",
            "snowflake.user=", "snowflake.password=", "snowflake.warehouse=", "snowflake.database=",
            "snowflake.role=" })
    @AutoConfigureMockMvc
    @ActiveProfiles("local")
    class WithoutServerSettings {

        @Autowired
        MockMvc mockMvc;

        @Test
        void defaultsAreBlankNotNull() throws Exception {
            mockMvc.perform(get("/api/me/connection/defaults"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.account").value(""))
                    .andExpect(jsonPath("$.user").value(""))
                    .andExpect(jsonPath("$.warehouse").value(""))
                    .andExpect(jsonPath("$.database").value(""))
                    .andExpect(jsonPath("$.role").value(""))
                    .andExpect(jsonPath("$.password").doesNotExist());
        }

        @Test
        void nothingIsSaved() throws Exception {
            mockMvc.perform(get("/api/me/connection"))
                    .andExpect(jsonPath("$.state").value("NONE"))
                    .andExpect(jsonPath("$.connected").value(false));
        }
    }
}
