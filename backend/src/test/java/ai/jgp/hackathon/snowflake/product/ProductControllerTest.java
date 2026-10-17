package ai.jgp.hackathon.snowflake.product;

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
import org.springframework.test.web.servlet.MvcResult;

// Sign-in is covered by AuthTest; here every request runs as the demo user.
@SpringBootTest(properties = "contextbuilder.auth.required=false")
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ProductControllerTest {

    @Autowired
    MockMvc mockMvc;

    static final String SELECTION = """
            {"database":"FINSERV_DW","schemas":[
              {"schema":"INSURANCE","tables":["CLAIMS"]},
              {"schema":"CORE","tables":[]}]}""";

    /** The contract id is a deterministic UUID v5 of the contract's name (DIND#110). */
    static final String INSURANCE_CONTRACT_ID =
            ContractGenerator.artifactId(ContractGenerator.contractName("FINSERV_DW", "INSURANCE"));

    @Test
    void generatesValidBundle() throws Exception {
        mockMvc.perform(post("/api/products/generate").contentType(MediaType.APPLICATION_JSON).content(SELECTION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validation.valid").value(true))
                .andExpect(jsonPath("$.contracts.length()").value(2))
                .andExpect(jsonPath("$.contracts[0].schema.length()").value(1))
                .andExpect(jsonPath("$.contracts[1].schema.length()").value(3))
                .andExpect(jsonPath("$.product.outputPorts[0].contractId").value(INSURANCE_CONTRACT_ID));
    }

    @Test
    void rendersYaml() throws Exception {
        MvcResult generated = mockMvc.perform(post("/api/products/generate")
                .contentType(MediaType.APPLICATION_JSON).content(SELECTION)).andReturn();
        mockMvc.perform(post("/api/products/yaml").contentType(MediaType.APPLICATION_JSON)
                        .content(generated.getResponse().getContentAsString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product").value(org.hamcrest.Matchers.containsString("kind: DataProduct")))
                .andExpect(jsonPath("$.contracts['" + INSURANCE_CONTRACT_ID + "']")
                        .value(org.hamcrest.Matchers.containsString("apiVersion: v3.2.0")));
    }

    @Test
    void unknownTableIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/products/generate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"database\":\"FINSERV_DW\",\"schemas\":[{\"schema\":\"CORE\",\"tables\":[\"NOPE\"]}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void emptySelectionIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/products/generate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"database\":\"FINSERV_DW\",\"schemas\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blankSchemaNameIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/products/generate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"database\":\"FINSERV_DW\",\"schemas\":[{\"schema\":\" \"}]}"))
                .andExpect(status().isBadRequest());
    }
}
