package ai.jgp.hackathon.snowflake.osi;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import ai.jgp.hackathon.snowflake.enrich.ProposalApplier;
import ai.jgp.hackathon.snowflake.enrich.Proposal;
import ai.jgp.hackathon.snowflake.product.ContractGenerator;
import ai.jgp.hackathon.snowflake.product.StandardsValidator;
import ai.jgp.hackathon.snowflake.stub.Finserv;

class OsiExporterTest {

    final StandardsValidator validator = new StandardsValidator();

    List<Map<String, Object>> contracts() {
        List<Map<String, Object>> contracts = new ArrayList<>();
        Finserv.SCHEMAS.forEach((schema, tables) -> contracts.add(ContractGenerator.contract("FINSERV_DW", schema,
                tables, new ContractGenerator.Server("acct", "WH"), Instant.EPOCH)));
        return ProposalApplier.apply(contracts, List.of(
                new Proposal("1", "finserv_dw-core", "CUSTOMERS", "EMAIL", "classification", "restricted", ""),
                new Proposal("2", "finserv_dw-core", "CUSTOMERS", "EMAIL", "tag", "pii", ""),
                new Proposal("3", "finserv_dw-core", "CUSTOMERS", "EMAIL", "businessName", "Email Address", ""),
                new Proposal("4", "finserv_dw-core", "CUSTOMERS", null, "description", "Bank customers.", "")));
    }

    @SuppressWarnings("unchecked")
    @Test
    void exportValidatesAgainstOssieSchema() {
        List<Map<String, Object>> contracts = contracts();
        Map<String, Object> model = OsiExporter.export(ContractGenerator.product("FINSERV_DW", contracts, Instant.EPOCH),
                contracts);
        assertThat(validator.validateOsi(model)).isEmpty();
        assertThat(model).containsEntry("version", "0.2.0.dev0").containsEntry("name", "finserv_dw");
        List<Map<String, Object>> datasets = (List<Map<String, Object>>) model.get("datasets");
        assertThat(datasets).hasSize(10);
        Map<String, Object> customers = datasets.get(1);
        assertThat(customers).containsEntry("name", "customers").containsEntry("source", "FINSERV_DW.CORE.CUSTOMERS")
                .containsEntry("primary_key", List.of("customer_id")).containsEntry("description", "Bank customers.");
        Map<String, Object> email = ((List<Map<String, Object>>) customers.get("fields")).get(4);
        assertThat(email).containsEntry("name", "email").containsEntry("datatype", "String")
                .containsEntry("ai_context", Map.of("synonyms", List.of("Email Address")));
        assertThat(email.get("custom_extensions").toString()).contains("restricted").contains("pii");
    }

    @SuppressWarnings("unchecked")
    @Test
    void infersRelationshipsFromKeyNames() {
        List<Map<String, Object>> contracts = contracts();
        Map<String, Object> model = OsiExporter.export(Map.of("id", "p"), contracts);
        List<Map<String, Object>> rels = (List<Map<String, Object>>) model.get("relationships");
        assertThat(rels).extracting(r -> r.get("name")).contains("customers_to_companies", "accounts_to_customers",
                "claims_to_policies", "customer_360_to_customers");
        assertThat(rels).extracting(r -> r.get("name")).doesNotContain("customers_to_customers");
    }

    @Test
    void datatypes() {
        assertThat(OsiExporter.datatype(Map.of("logicalType", "number", "physicalType", "FLOAT"))).isEqualTo("Float");
        assertThat(OsiExporter.datatype(Map.of("logicalType", "number", "physicalType", "NUMBER(18,2)")))
                .isEqualTo("Decimal");
        assertThat(OsiExporter.datatype(Map.of("logicalType", "timestamp", "physicalType", "TIMESTAMP_TZ")))
                .isEqualTo("DateTimeTz");
        assertThat(OsiExporter.datatype(Map.of("logicalType", "timestamp", "physicalType", "TIMESTAMP_NTZ")))
                .isEqualTo("DateTime");
        assertThat(OsiExporter.datatype(Map.of("logicalType", "timestamp", "physicalType", "TIMESTAMP_LTZ")))
                .isEqualTo("DateTimeTz");
        assertThat(OsiExporter.datatype(Map.of("logicalType", "object"))).isEqualTo("Opaque");
    }

    @Test
    void invalidModelIsReported() {
        assertThat(validator.validateOsi(Map.of("version", "9", "name", "x"))).isNotEmpty();
    }
}
