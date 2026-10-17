package ai.jgp.hackathon.snowflake.enrich;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import ai.jgp.hackathon.snowflake.product.Bundle;
import ai.jgp.hackathon.snowflake.product.ContractGenerator;
import ai.jgp.hackathon.snowflake.product.StandardsValidator;
import ai.jgp.hackathon.snowflake.stub.Finserv;

/** The RFC-0038 context block: what Cortex may propose, and how the levels above a table compose (DIND#109). */
class ContextBlocksTest {

    static final Instant NOW = Instant.parse("2026-10-16T17:00:00Z");
    static final ContractGenerator.Server SERVER = new ContractGenerator.Server("acme-lab", "DEFAULT_WH");
    final StandardsValidator validator = new StandardsValidator();

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object o) {
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    static List<Object> asList(Object o) {
        return (List<Object>) o;
    }

    static Map<String, Object> block(String instructions) {
        return Map.of(
                "instructions", instructions,
                "verifiedStatements", List.of(Map.of("question", "How many claims per policy?",
                        "answer", "One per policy, in CLAIMS.")),
                "constraints", List.of(Map.of("constraint", "Never sum PREMIUM across policies.")));
    }

    static List<Map<String, Object>> contracts() {
        List<Map<String, Object>> contracts = new ArrayList<>();
        Finserv.SCHEMAS.forEach((schema, tables) ->
                contracts.add(ContractGenerator.contract("FINSERV_DW", schema, tables, SERVER, NOW)));
        return contracts;
    }

    /** Puts a block on every table of a contract, as accepting one proposal per table would. */
    static void giveEveryTableABlock(Map<String, Object> contract) {
        for (Object o : asList(contract.get("schema"))) {
            Map<String, Object> table = asMap(o);
            table.put("context", ContextBlocks.sanitize(block("Guidance for " + table.get("name"))));
        }
    }

    @Test
    void keepsOnlyTheFieldsBothStandardsAllow() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("instructions", "  Use CLAIMS for policy-level questions.  ");
        raw.put("verifiedStatements", List.of(
                Map.of("question", "How many claims?", "answer", "One per policy.", "id", "q1", "tags", List.of("claims"),
                        "somethingElse", "dropped"),
                Map.of("answer", "no question, so not a statement")));
        raw.put("constraints", List.of(Map.of("constraint", "Do not sum PREMIUM.", "id", "c1")));
        raw.put("unknownKey", "dropped");

        Map<String, Object> block = ContextBlocks.sanitize(raw);

        assertThat(block).containsOnlyKeys("instructions", "verifiedStatements", "constraints");
        assertThat(block).containsEntry("instructions", "Use CLAIMS for policy-level questions.");
        List<Object> statements = asList(block.get("verifiedStatements"));
        assertThat(statements).hasSize(1);
        assertThat(asMap(statements.get(0))).containsOnlyKeys("id", "question", "answer", "tags");
    }

    @Test
    void capsEntriesAndDropsTheOnesWithoutTheirText() {
        List<Object> many = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            many.add(Map.of("question", "q" + i));
        }
        Map<String, Object> block = ContextBlocks.sanitize(Map.of(
                "verifiedStatements", many,
                "constraints", List.of(Map.of("constraint", "  "), Map.of("constraint", "Keep this one."))));

        assertThat(asList(block.get("verifiedStatements"))).hasSize(ContextBlocks.MAX_STATEMENTS);
        assertThat(asList(block.get("constraints"))).hasSize(1);
    }

    @Test
    void anEmptyBlockIsNothingRatherThanAnEmptyObject() {
        assertThat(ContextBlocks.sanitize(Map.of())).isNull();
        assertThat(ContextBlocks.sanitize(Map.of("instructions", "   "))).isNull();
        assertThat(ContextBlocks.sanitize(Map.of("verifiedStatements", List.of(Map.of("answer", "orphan"))))).isNull();
    }

    @Test
    void aContractAndItsProductComposeFromTheTablesAndNameThem() {
        List<Map<String, Object>> contracts = contracts();
        giveEveryTableABlock(contracts.get(0));

        ContextBlocks.composeContract(contracts.get(0));
        Map<String, Object> product = ContextBlocks.composeProduct(
                ContractGenerator.product("FINSERV_DW", contracts, NOW), contracts);

        Map<String, Object> contractContext = asMap(contracts.get(0).get("context"));
        assertThat(String.valueOf(contractContext.get("instructions"))).contains("Guidance for ").contains(": ");
        List<Object> statements = asList(contractContext.get("verifiedStatements"));
        String firstTable = String.valueOf(asMap(asList(contracts.get(0).get("schema")).get(0)).get("name"));
        assertThat(asMap(statements.get(0))).containsEntry("tags", List.of(firstTable));

        // The contract with no blocks carries no key at all, rather than an empty one.
        assertThat(contracts.get(1)).doesNotContainKey("context");

        assertThat(asList(asMap(product.get("context")).get("verifiedStatements"))).isNotEmpty();

        List<Object> ports = asList(product.get("outputPorts"));
        assertThat(asMap(ports.get(0))).containsKey("context");
        assertThat(asMap(ports.get(1))).doesNotContainKey("context");
    }

    @Test
    void nothingAcceptedLeavesNoContextKeyAtAnyLevel() {
        List<Map<String, Object>> contracts = contracts();
        Map<String, Object> product = ContextBlocks.composeProduct(
                ContractGenerator.product("FINSERV_DW", contracts, NOW), contracts);
        contracts.forEach(ContextBlocks::composeContract);

        assertThat(product).doesNotContainKey("context");
        contracts.forEach(c -> assertThat(c).doesNotContainKey("context"));
        for (Object port : asList(product.get("outputPorts"))) {
            assertThat(asMap(port)).doesNotContainKey("context");
        }
    }

    @Test
    void everyLevelWithABlockValidatesAgainstItsSchema() {
        List<Map<String, Object>> contracts = contracts();
        contracts.forEach(ContextBlocksTest::giveEveryTableABlock);
        contracts.forEach(ContextBlocks::composeContract);
        Map<String, Object> product = ContextBlocks.composeProduct(
                ContractGenerator.product("FINSERV_DW", contracts, NOW), contracts);

        // Table, contract, product and port: all four carry a block...
        for (Map<String, Object> contract : contracts) {
            for (Object table : asList(contract.get("schema"))) {
                assertThat(asMap(table)).containsKey("context");
            }
            assertThat(contract).containsKey("context");
        }
        assertThat(product).containsKey("context");
        for (Object port : asList(product.get("outputPorts"))) {
            assertThat(asMap(port)).containsKey("context");
        }

        // ...and all four validate: ODCS over the contracts, ODPS over the product.
        Bundle.Validation validation = validator.validate(product, contracts);
        assertThat(validation.errors()).isEmpty();
        assertThat(validation.valid()).isTrue();
    }

    @Test
    void aContextProposalLandsOnItsTableAndNowhereElse() {
        List<Map<String, Object>> contracts = contracts();
        String contractId = String.valueOf(contracts.get(0).get("id"));
        List<Map<String, Object>> applied = ProposalApplier.apply(contracts, List.of(
                new Proposal("p1", contractId, "CUSTOMERS", null, Proposal.CONTEXT, block("Customers first."), "")));

        List<Object> schema = asList(applied.get(0).get("schema"));
        assertThat(asMap(schema.get(1))).containsKey("context");
        assertThat(asMap(schema.get(0))).doesNotContainKey("context");

        // A column can never carry one: both standards allow context on a table or above it only.
        Throwable thrown = catchThrowable(() -> ProposalApplier.apply(contracts, List.of(
                new Proposal("p2", contractId, "CUSTOMERS", "EMAIL", Proposal.CONTEXT, block("x"), ""))));
        assertThat(thrown).hasMessageContaining("not a column");
    }
}
