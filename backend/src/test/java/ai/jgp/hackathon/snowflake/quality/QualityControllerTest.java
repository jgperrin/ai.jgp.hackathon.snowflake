package ai.jgp.hackathon.snowflake.quality;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

// Sign-in is covered by AuthTest; here every request runs as the demo user.
@SpringBootTest(properties = "contextbuilder.auth.required=false")
@AutoConfigureMockMvc
@ActiveProfiles("local")
class QualityControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void runsRulesOfABundle() throws Exception {
        String bundle = """
                {"product": {}, "contracts": [{"id": "c1",
                  "servers": [{"database": "FINSERV_DW", "schema": "INSURANCE"}],
                  "schema": [{"name": "CLAIMS", "quality": [{"metric": "rowCount", "mustBeGreaterThan": 0}],
                    "properties": [{"name": "CLAIM_STATUS", "quality": [{"metric": "invalidValues",
                      "arguments": {"validValues": ["OPEN"]}, "mustBe": 0}]}]}]}]}""";
        mockMvc.perform(post("/api/quality/run").contentType(MediaType.APPLICATION_JSON).content(bundle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(1))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.results[0].observed").value(14000.0))
                .andExpect(jsonPath("$.results[1].observed").value(12.0));
    }
}
