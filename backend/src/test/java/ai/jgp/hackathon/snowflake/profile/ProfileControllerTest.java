package ai.jgp.hackathon.snowflake.profile;

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
class ProfileControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void profilesSelectedTables() throws Exception {
        mockMvc.perform(post("/api/profile").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"database\":\"FINSERV_DW\",\"schema\":\"CORE\",\"tables\":[\"CUSTOMERS\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tables.length()").value(1))
                .andExpect(jsonPath("$.tables[0].sampled").value(false))
                .andExpect(jsonPath("$.tables[0].columns[7].column").value("CUSTOMER_TIER"))
                .andExpect(jsonPath("$.tables[0].columns[7].topValues.length()").value(3));
    }

    @Test
    void badTableNameIsRejected() throws Exception {
        mockMvc.perform(post("/api/profile").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"database\":\"FINSERV_DW\",\"schema\":\"CORE\",\"tables\":[\"X;DROP\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownTableIsRejected() throws Exception {
        mockMvc.perform(post("/api/profile").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"database\":\"FINSERV_DW\",\"schema\":\"CORE\",\"tables\":[\"NOPE\"]}"))
                .andExpect(status().isBadRequest());
    }
}
