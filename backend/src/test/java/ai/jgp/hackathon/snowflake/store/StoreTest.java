package ai.jgp.hackathon.snowflake.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import ai.jgp.hackathon.snowflake.store.StoreModel.ArtifactSummary;
import ai.jgp.hackathon.snowflake.store.StoreModel.SavedSave;
import ai.jgp.hackathon.snowflake.store.StoreModel.StoredRow;
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
                + StoreSql.latest("DB") + StoreSql.load("DB") + StoreSql.saves("DB");
        assertThat(all).doesNotContainIgnoringCase("DROP").doesNotContainIgnoringCase("DELETE")
                .doesNotContainIgnoringCase("UPDATE ").doesNotContainIgnoringCase("TRUNCATE")
                .contains("IF NOT EXISTS").contains("PARSE_JSON(?)");
        assertThat(StoreSql.table("DB")).isEqualTo("\"DB\".\"DATA_PRODUCTS\".\"ARTIFACTS\"");
    }

    @Test
    void savesSqlReadsEveryKindNewestFirstAndBounded() {
        String sql = StoreSql.saves("DB");
        assertThat(sql).startsWith("SELECT").contains("SAVED_BY").contains("\"DB\".\"DATA_PRODUCTS\".\"ARTIFACTS\"")
                .contains("ORDER BY SAVED_AT DESC, SAVE_ID, KIND").endsWith("LIMIT 200")
                .doesNotContain("QUALIFY").doesNotContain("KIND = 'ODPS'");
    }

    private static StoredRow row(String saveId, String savedAt, String kind, String name) {
        return new StoredRow(saveId, "p-" + saveId, savedAt, "JGP",
                new ArtifactSummary(name.toLowerCase(), kind, name, "1.0.0", "active"));
    }

    @Test
    void flatRowsAreGroupedIntoSavesNewestFirstProductFirst() {
        List<SavedSave> saves = StoreService.group(List.of(
                row("a", "2026-10-15T10:00:00Z", "ODPS", "OLD"),
                row("b", "2026-10-16T10:00:00Z", "OSI", "MODEL"),
                row("b", "2026-10-16T10:00:00Z", "ODCS", "RISK"),
                row("b", "2026-10-16T10:00:00Z", "ODPS", "NEW"),
                row("b", "2026-10-16T10:00:00Z", "ODCS", "CORE")));
        assertThat(saves).extracting(SavedSave::saveId).containsExactly("b", "a");
        SavedSave newest = saves.get(0);
        assertThat(newest.name()).isEqualTo("NEW");
        assertThat(newest.productId()).isEqualTo("p-b");
        assertThat(newest.savedBy()).isEqualTo("JGP");
        assertThat(newest.artifacts()).extracting(ArtifactSummary::kind).containsExactly("ODPS", "ODCS", "ODCS", "OSI");
        assertThat(newest.artifacts()).extracting(ArtifactSummary::name).containsExactly("NEW", "CORE", "RISK", "MODEL");
        assertThat(saves.get(1).artifacts()).hasSize(1);
    }

    @Test
    void saveWithoutProductRowStillListsWithNoName() {
        List<SavedSave> saves = StoreService.group(List.of(row("c", "2026-10-16T10:00:00Z", "ODCS", "ONLY")));
        assertThat(saves).hasSize(1);
        assertThat(saves.get(0).name()).isNull();
        assertThat(saves.get(0).version()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void savesAndArtifactsRoutesListEverySave() throws Exception {
        String bundle = mockMvc.perform(post("/api/products/generate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"database\":\"FINSERV_DW\",\"schemas\":[{\"schema\":\"RISK\",\"tables\":[]},"
                        + "{\"schema\":\"CORE\",\"tables\":[]}]}"))
                .andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(post("/api/products/save").contentType(MediaType.APPLICATION_JSON)
                .content(bundle)).andReturn().getResponse().getContentAsString();
        String id = (String) json.readValue(second, Map.class).get("id");

        String first = mockMvc.perform(post("/api/products/save").contentType(MediaType.APPLICATION_JSON)
                .content(bundle)).andReturn().getResponse().getContentAsString();
        String firstId = (String) json.readValue(first, Map.class).get("id");

        // Both saves of the same product are listed, which /api/products collapses to one.
        String body = mockMvc.perform(get("/api/products/saves"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> saves = json.readValue(body, List.class);
        assertThat(saves).extracting(s -> s.get("saveId")).contains(id, firstId);
        assertThat(saves).extracting(s -> (String) s.get("savedAt")).isSortedAccordingTo(
                java.util.Comparator.reverseOrder());
        Map<String, Object> mine = saves.stream().filter(s -> id.equals(s.get("saveId"))).findFirst().orElseThrow();
        assertThat(mine.get("name")).isEqualTo("FINSERV_DW");
        assertThat(mine.get("version")).isEqualTo("1.0.0");
        assertThat(mine.get("savedBy")).isEqualTo("jgp");
        List<Map<String, Object>> artifacts = (List<Map<String, Object>>) mine.get("artifacts");
        assertThat(artifacts).extracting(x -> x.get("kind")).containsExactly("ODPS", "ODCS", "ODCS", "OSI");

        mockMvc.perform(get("/api/products/" + id + "/artifacts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].kind").value("ODPS"))
                .andExpect(jsonPath("$[0].yaml").value(org.hamcrest.Matchers.containsString("kind: DataProduct")))
                .andExpect(jsonPath("$[3].kind").value("OSI"));
    }

    @Test
    void artifactsRouteGuardsTheId() throws Exception {
        mockMvc.perform(get("/api/products/x';--/artifacts")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/products/00000000-0000-0000-0000-000000000000/artifacts"))
                .andExpect(status().isBadRequest());
    }
}
