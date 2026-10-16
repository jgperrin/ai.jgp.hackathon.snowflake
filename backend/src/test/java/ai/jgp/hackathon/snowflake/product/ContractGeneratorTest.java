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
        assertThat(ports.get(2)).containsEntry("name", "insurance").containsEntry("contractId", "finserv_dw-insurance");
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
