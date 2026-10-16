package ai.jgp.hackathon.snowflake.osi;

import static org.hamcrest.Matchers.containsString;
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
class OsiControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void exportsGeneratedBundle() throws Exception {
        String bundle = mockMvc.perform(post("/api/products/generate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"database\":\"FINSERV_DW\",\"schemas\":[{\"schema\":\"CORE\",\"tables\":[]}]}"))
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/export/osi").contentType(MediaType.APPLICATION_JSON).content(bundle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.yaml").value(containsString("version: 0.2.0.dev0")))
                .andExpect(jsonPath("$.yaml").value(containsString("source: FINSERV_DW.CORE.CUSTOMERS")));
    }
}
