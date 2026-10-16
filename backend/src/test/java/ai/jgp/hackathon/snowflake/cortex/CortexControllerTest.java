package ai.jgp.hackathon.snowflake.cortex;

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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class CortexControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void localProfileServesStubbedAnswersWithoutSnowflake() throws Exception {
        mockMvc.perform(post("/api/cortex/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"what is Cortex?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(
                        "Stubbed answer (no Snowflake connection). You asked: what is Cortex?"));
    }

    @Test
    void blankPromptIsRejected() throws Exception {
        mockMvc.perform(post("/api/cortex/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }
}
