package ai.jgp.hackathon.snowflake.enrich;

import static org.assertj.core.api.Assertions.assertThat;
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

import tools.jackson.databind.json.JsonMapper;

// Sign-in is covered by AuthTest; here every request runs as the demo user.
@SpringBootTest(properties = "contextbuilder.auth.required=false")
@AutoConfigureMockMvc
@ActiveProfiles("local")
class EnrichControllerTest {

    @Autowired
    MockMvc mockMvc;

    final JsonMapper json = JsonMapper.builder().build();

    String bundle() throws Exception {
        return mockMvc.perform(post("/api/products/generate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"database\":\"FINSERV_DW\",\"schemas\":[{\"schema\":\"INSURANCE\",\"tables\":[]}]}"))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @SuppressWarnings("unchecked")
    void proposesThenAppliesOnlyAcceptedAndStaysValid() throws Exception {
        String bundle = bundle();
        String enriched = mockMvc.perform(post("/api/enrich").contentType(MediaType.APPLICATION_JSON).content(bundle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposals[?(@.kind == 'qualityRule')]").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> proposals = (List<Map<String, Object>>) json.readValue(enriched, Map.class)
                .get("proposals");
        // Accept everything except the table description of CLAIMS: it must not land.
        List<Map<String, Object>> accepted = proposals.stream()
                .filter(p -> !("CLAIMS".equals(p.get("table")) && p.get("column") == null
                        && "description".equals(p.get("kind"))))
                .toList();
        String request = "{\"bundle\":" + bundle + ",\"accepted\":" + json.writeValueAsString(accepted) + "}";
        String applied = mockMvc.perform(post("/api/products/apply").contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validation.valid").value(true))
                .andExpect(jsonPath("$.contracts[0].slaProperties[0].property").value("freshness"))
                .andExpect(jsonPath("$.contracts[0].schema[1].businessName").value("Insurance Claims"))
                .andExpect(jsonPath("$.contracts[0].schema[1].quality[0].type").value("sql"))
                .andExpect(jsonPath("$.contracts[0].schema[1].properties[0].quality[0].metric")
                        .value("duplicateValues"))
                .andReturn().getResponse().getContentAsString();
        assertThat(applied).doesNotContain("Insurance claims filed against policies");
    }

    @Test
    void unknownTargetIsBadRequest() throws Exception {
        String request = "{\"bundle\":" + bundle() + ",\"accepted\":[{\"id\":\"p1\",\"contractId\":\"finserv_dw-insurance\","
                + "\"table\":\"NOPE\",\"column\":null,\"kind\":\"description\",\"value\":\"x\",\"rationale\":\"\"}]}";
        mockMvc.perform(post("/api/products/apply").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest());
    }

    @Test
    void appliesTagsAndClassification() {
        List<Map<String, Object>> contracts = List.of(new java.util.HashMap<>(Map.of("id", "c",
                "schema", List.of(new java.util.HashMap<>(Map.of("name", "T",
                        "properties", List.of(new java.util.HashMap<>(Map.of("name", "EMAIL"))))))))); 
        List<Map<String, Object>> out = ProposalApplier.apply(contracts, List.of(
                new Proposal("1", "c", "T", "EMAIL", "tag", "pii", ""),
                new Proposal("2", "c", "T", "EMAIL", "tag", "pii", ""),
                new Proposal("3", "c", "T", "EMAIL", "classification", "restricted", "")));
        assertThat(out.toString()).contains("tags=[pii]").contains("classification=restricted");
        assertThat(contracts.toString()).doesNotContain("pii");
    }
}
