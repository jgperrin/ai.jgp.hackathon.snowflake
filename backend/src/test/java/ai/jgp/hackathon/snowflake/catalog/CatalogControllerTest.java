package ai.jgp.hackathon.snowflake.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

// Sign-in is covered by AuthTest; here every request runs as the demo user.
@SpringBootTest(properties = "contextbuilder.auth.required=false")
@AutoConfigureMockMvc
@ActiveProfiles("local")
class CatalogControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void listsDatabases() throws Exception {
        mockMvc.perform(get("/api/catalog/databases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("FINSERV_DW"))
                .andExpect(jsonPath("$[0].system").value(false));
    }

    @Test
    void listsSchemas() throws Exception {
        mockMvc.perform(get("/api/catalog/databases/FINSERV_DW/schemas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("CORE"))
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void listsTablesWithColumnsAndKeys() throws Exception {
        mockMvc.perform(get("/api/catalog/databases/FINSERV_DW/schemas/INSURANCE/tables"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].name").value("CLAIMS"))
                .andExpect(jsonPath("$[1].kind").value("TABLE"))
                .andExpect(jsonPath("$[1].rowCount").value(14000))
                .andExpect(jsonPath("$[1].columns[0].name").value("CLAIM_ID"))
                .andExpect(jsonPath("$[1].columns[0].primaryKey").value(true))
                .andExpect(jsonPath("$[1].columns[2].ordinal").value(3));
    }

    @Test
    void injectionAttemptInIdentifierIsRejected() throws Exception {
        mockMvc.perform(get("/api/catalog/databases/FINSERV_DW\";DROP/schemas"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void unknownSchemaIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/catalog/databases/FINSERV_DW/schemas/NOPE/tables"))
                .andExpect(status().isBadRequest());
    }
}
