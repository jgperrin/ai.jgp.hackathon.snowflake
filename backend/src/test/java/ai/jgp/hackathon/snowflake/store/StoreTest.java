package ai.jgp.hackathon.snowflake.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.json.JsonMapper;

// Sign-in is covered by AuthTest; here every request runs as the demo user.
@SpringBootTest(properties = "contextbuilder.auth.required=false")
@AutoConfigureMockMvc
@ActiveProfiles("local")
class StoreTest {

    @Autowired
    MockMvc mockMvc;

    final JsonMapper json = JsonMapper.builder().build();

    @Test
    void saveThenLoadRoundTrips() throws Exception {
        String bundle = mockMvc.perform(post("/api/products/generate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"database\":\"FINSERV_DW\",\"schemas\":[{\"schema\":\"RISK\",\"tables\":[]}]}"))
                .andReturn().getResponse().getContentAsString();
        String saved = mockMvc.perform(post("/api/products/save").contentType(MediaType.APPLICATION_JSON).content(bundle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("1.0.0"))
                .andReturn().getResponse().getContentAsString();
        String id = (String) json.readValue(saved, Map.class).get("id");

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("FINSERV_DW"));
        String loaded = mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validation.valid").value(true))
                .andReturn().getResponse().getContentAsString();
        Map<?, ?> original = json.readValue(bundle, Map.class);
        Map<?, ?> roundTripped = json.readValue(loaded, Map.class);
        assertThat(roundTripped.get("product")).isEqualTo(original.get("product"));
        assertThat(roundTripped.get("contracts")).isEqualTo(original.get("contracts"));
    }

    @Test
    void badIdsAreRejected() throws Exception {
        mockMvc.perform(get("/api/products/x';--")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/products/00000000-0000-0000-0000-000000000000")).andExpect(status().isBadRequest());
    }

    @Test
    void sqlIsAppendOnly() {
        String all = StoreSql.createSchema("DB") + StoreSql.createTable("DB") + StoreSql.insert("DB")
                + StoreSql.latest("DB") + StoreSql.load("DB");
        assertThat(all).doesNotContainIgnoringCase("DROP").doesNotContainIgnoringCase("DELETE")
                .doesNotContainIgnoringCase("UPDATE ").doesNotContainIgnoringCase("TRUNCATE")
                .contains("IF NOT EXISTS").contains("PARSE_JSON(?)");
        assertThat(StoreSql.table("DB")).isEqualTo("\"DB\".\"DATA_PRODUCTS\".\"ARTIFACTS\"");
    }
}
