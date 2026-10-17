package ai.jgp.hackathon.snowflake.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import ai.jgp.hackathon.snowflake.catalog.CatalogModel.ColumnInfo;
import ai.jgp.hackathon.snowflake.catalog.CatalogModel.TableInfo;
import ai.jgp.hackathon.snowflake.stub.Finserv;

class ContractGeneratorTest {

    static final Instant NOW = Instant.parse("2026-10-16T17:00:00Z");
    static final ContractGenerator.Server SERVER = new ContractGenerator.Server("acme-lab", "DEFAULT_WH");
    final StandardsValidator validator = new StandardsValidator();

    List<Map<String, Object>> allContracts() {
        List<Map<String, Object>> contracts = new ArrayList<>();
        Finserv.SCHEMAS.forEach((schema, tables) ->
                contracts.add(ContractGenerator.contract("FINSERV_DW", schema, tables, SERVER, NOW)));
        return contracts;
    }

    @Test
    void generatedContractsValidateAgainstOdcs320() {
        List<Map<String, Object>> contracts = allContracts();
        Map<String, Object> product = ContractGenerator.product("FINSERV_DW", contracts, NOW);
        Bundle.Validation validation = validator.validate(product, contracts);
        assertThat(validation.errors()).isEmpty();
        assertThat(validation.valid()).isTrue();
        assertThat(contracts.get(0)).containsEntry("apiVersion", "v3.2.0").containsEntry("kind", "DataContract");
        assertThat(product).containsEntry("apiVersion", "v1.1.0").containsEntry("kind", "DataProduct");
    }

    @Test
    void eachContractBecomesAnOutputPort() {
        List<Map<String, Object>> contracts = allContracts();
        Map<String, Object> product = ContractGenerator.product("FINSERV_DW", contracts, NOW);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ports = (List<Map<String, Object>>) product.get("outputPorts");
        assertThat(ports).hasSize(5);
        assertThat(ports.get(2)).containsEntry("name", "insurance");
        // The port points at the contract it belongs to, and that id is a UUID v5 (DIND#110):
        // version nibble 5, variant 8-b, and derived from the contract's own name.
        assertThat(String.valueOf(ports.get(2).get("contractId"))).isEqualTo(String.valueOf(contracts.get(2).get("id")))
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-5[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    }

    @Test
    void artifactIdsAreUuidV5OfTheName() {
        // Known answers (cross-checked against Python's uuid.uuid5 in the same namespace). They pin
        // the algorithm, the namespace and the name encoding: changing any of the three would
        // reissue every id this app has ever produced, and this fails instead.
        assertThat(ContractGenerator.artifactId("FINSERV_DW data product"))
                .isEqualTo("f3889c50-a73f-5194-b163-5bdae1e5a1d7");
        assertThat(ContractGenerator.artifactId("FINSERV_DW.INSURANCE data contract"))
                .isEqualTo("92a45b25-c6fb-537b-bfbb-f2c34af21f48");
        assertThat(ContractGenerator.artifactId("AUTO_MFG_DW.SALES data contract"))
                .isEqualTo("794efb4c-faf9-5074-9897-1065489ec76b");
    }

    @Test
    void namesSayWhatTheArtifactIsAndIdsAreDeterministic() {
        assertThat(ContractGenerator.productName("HEALTHCARE_DW")).isEqualTo("HEALTHCARE_DW data product");
        assertThat(ContractGenerator.contractName("HEALTHCARE_DW", "CLAIMS"))
                .isEqualTo("HEALTHCARE_DW.CLAIMS data contract");

        List<Map<String, Object>> contracts = allContracts();
        Map<String, Object> product = ContractGenerator.product("FINSERV_DW", contracts, NOW);
        assertThat(product).containsEntry("name", "FINSERV_DW data product");
        assertThat(String.valueOf(product.get("id")))
                .isEqualTo(ContractGenerator.artifactId("FINSERV_DW data product"));
        assertThat(contracts.get(0)).containsEntry("name", ContractGenerator.contractName("FINSERV_DW", "CORE"))
                .containsEntry("dataProduct", "FINSERV_DW data product");

        // Deterministic: regenerating the same source yields the same ids...
        Map<String, Object> again = ContractGenerator.product("FINSERV_DW", allContracts(), NOW);
        assertThat(again.get("id")).isEqualTo(product.get("id"));
        // ...and two different sources never collide, which slugified ids could not promise.
        Map<String, Object> other = ContractGenerator.product("HEALTHCARE_DW", allContracts(), NOW);
        assertThat(other.get("id")).isNotEqualTo(product.get("id"));
        assertThat(ContractGenerator.artifactId("A.B data contract"))
                .isNotEqualTo(ContractGenerator.artifactId("A.B.C data contract"));
    }

    @Test
    void invalidDocumentIsReported() {
        Map<String, Object> contract = new java.util.LinkedHashMap<>(allContracts().get(0));
        contract.put("kind", "NotAContract");
        Bundle.Validation validation = validator.validate(
                ContractGenerator.product("FINSERV_DW", List.of(contract), NOW), List.of(contract));
        assertThat(validation.valid()).isFalse();
        assertThat(validation.errors()).anySatisfy(p -> assertThat(p.document()).startsWith("ODCS"));
    }

    @Test
    void columnsMapToProperties() {
        TableInfo claims = Finserv.SCHEMAS.get("INSURANCE").get(1);
        Map<String, Object> object = ContractGenerator.schemaObject(claims);
        assertThat(object).containsEntry("name", "CLAIMS").containsEntry("physicalType", "table");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> props = (List<Map<String, Object>>) object.get("properties");
        assertThat(props.get(0)).containsEntry("primaryKey", true).containsEntry("primaryKeyPosition", 1)
                .containsEntry("logicalType", "integer").containsEntry("required", true);
        assertThat(props.get(4)).containsEntry("logicalType", "number").containsEntry("physicalType", "NUMBER(18,2)");
    }

    @ParameterizedTest
    @CsvSource({
        "NUMBER,0,integer", "NUMBER,2,number", "TEXT,,string", "DATE,,date", "TIMESTAMP_NTZ,,timestamp",
        "TIMESTAMP_TZ,,timestamp", "BOOLEAN,,boolean", "VARIANT,,object", "OBJECT,,object", "ARRAY,,array",
        "FLOAT,,number", "TIME,,time", "BINARY,,string"
    })
    void typeMapping(String type, Integer scale, String expected) {
        ColumnInfo column = new ColumnInfo("C", type, true, null, 1, false, null, 38, scale, null);
        assertThat(TypeMapper.logicalType(column)).isEqualTo(expected);
    }

    @Test
    void idsFollowStableIdRules() {
        assertThat(ContractGenerator.id("FINSERV_DW", "My Schema.x")).isEqualTo("finserv_dw-my_schema_x");
    }
}
