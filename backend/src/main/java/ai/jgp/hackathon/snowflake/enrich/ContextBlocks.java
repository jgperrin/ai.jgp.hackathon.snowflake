package ai.jgp.hackathon.snowflake.enrich;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The AI-and-semantic context block both standards carry (RFC-0038): {@code instructions} for an
 * agent, {@code verifiedStatements} it can answer from, and {@code constraints} it must respect.
 *
 * <p>Cortex proposes one per table, a human accepts or edits it, and it lands on that table. The
 * contract's own block and the product's, and each output port's, are then composed from the
 * accepted table blocks — deterministically, so the same accepted set always produces the same
 * documents, and no second model call is involved (DIND#109).
 *
 * <p>Where each level is allowed comes from the bundled schemas: ODCS v3.2 has {@code context} at
 * the top level and on a schema object, not on a property; ODPS v1.1 has it at the top level and on
 * an output port. The blocks below are written to satisfy both, whose entry shapes agree on
 * {@code id}, {@code question}, {@code answer}, {@code constraint} and {@code tags}.
 */
public final class ContextBlocks {

    /** How many entries one table may contribute. */
    static final int MAX_STATEMENTS = 4;
    static final int MAX_CONSTRAINTS = 3;

    /**
     * How many entries a composed product or port block may carry. A product spanning many tables
     * would otherwise repeat every table's questions; the contracts keep the full set, and the
     * product carries enough to prime an agent.
     */
    static final int MAX_COMPOSED_STATEMENTS = 12;
    static final int MAX_COMPOSED_CONSTRAINTS = 8;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ContextBlocks() {
    }

    /** Returns a clean block, or null when there is nothing usable in the input. */
    public static Map<String, Object> sanitize(Map<?, ?> raw) {
        Map<String, Object> block = new LinkedHashMap<>();
        String instructions = string(raw.get("instructions"));
        if (instructions != null && !instructions.isBlank()) {
            block.put("instructions", instructions.strip());
        }
        List<Object> statements = entries(raw.get("verifiedStatements"), "question", MAX_STATEMENTS);
        if (!statements.isEmpty()) {
            block.put("verifiedStatements", statements);
        }
        List<Object> constraints = entries(raw.get("constraints"), "constraint", MAX_CONSTRAINTS);
        if (!constraints.isEmpty()) {
            block.put("constraints", constraints);
        }
        return block.isEmpty() ? null : block;
    }

    /**
     * Keeps the entries that carry their required text, up to the cap, and only the fields both
     * standards allow ({@code additionalProperties} is false in each).
     */
    private static List<Object> entries(Object raw, String required, int cap) {
        List<Object> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return out;
        }
        for (Object o : list) {
            if (out.size() >= cap) {
                break;
            }
            if (!(o instanceof Map<?, ?> entry)) {
                continue;
            }
            String text = string(entry.get(required));
            if (text == null || text.isBlank()) {
                continue;
            }
            Map<String, Object> clean = new LinkedHashMap<>();
            String id = string(entry.get("id"));
            if (id != null && !id.isBlank()) {
                clean.put("id", id.strip());
            }
            clean.put(required, text.strip());
            if (entry.get("answer") instanceof String answer && !answer.isBlank()) {
                clean.put("answer", answer.strip());
            }
            if (entry.get("tags") instanceof List<?> tags) {
                List<String> kept = tags.stream().map(ContextBlocks::string).filter(t -> t != null && !t.isBlank())
                        .map(String::strip).toList();
                if (!kept.isEmpty()) {
                    clean.put("tags", kept);
                }
            }
            out.add(clean);
        }
        return out;
    }

    /**
     * Puts a contract's own block on it, composed from its tables' accepted blocks, or removes the
     * key when no table has one. Mutates the contract, which the applier has already copied.
     */
    public static void composeContract(Map<String, Object> contract) {
        List<TableBlock> tables = tableBlocks(contract);
        if (tables.isEmpty()) {
            contract.remove("context");
            return;
        }
        contract.put("context", compose(tables, MAX_COMPOSED_STATEMENTS, MAX_COMPOSED_CONSTRAINTS));
    }

    /**
     * Returns a copy of the product with its own block and each output port's composed from the
     * contracts behind them, or with the keys removed when nothing was accepted. The product is
     * copied rather than mutated: it belongs to the caller's bundle.
     */
    public static Map<String, Object> composeProduct(Map<String, Object> product,
            List<Map<String, Object>> contracts) {
        Map<String, Object> copy = JSON.convertValue(product, new TypeReference<>() {
        });

        List<TableBlock> everyTable = new ArrayList<>();
        contracts.forEach(c -> everyTable.addAll(tableBlocks(c)));
        if (everyTable.isEmpty()) {
            copy.remove("context");
        } else {
            copy.put("context", compose(everyTable, MAX_COMPOSED_STATEMENTS, MAX_COMPOSED_CONSTRAINTS));
        }

        Map<String, List<TableBlock>> byContractId = new LinkedHashMap<>();
        for (Map<String, Object> contract : contracts) {
            byContractId.put(String.valueOf(contract.get("id")), tableBlocks(contract));
        }
        for (Object o : list(copy.get("outputPorts"))) {
            if (!(o instanceof Map<?, ?> port)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> portMap = (Map<String, Object>) port;
            List<TableBlock> tables = byContractId.getOrDefault(String.valueOf(portMap.get("contractId")), List.of());
            if (tables.isEmpty()) {
                portMap.remove("context");
            } else {
                portMap.put("context", compose(tables, MAX_COMPOSED_STATEMENTS, MAX_COMPOSED_CONSTRAINTS));
            }
        }
        return copy;
    }

    /** One table's accepted block, with the table's name to attribute it to. */
    private record TableBlock(String table, Map<?, ?> block) {
    }

    private static List<TableBlock> tableBlocks(Map<String, Object> contract) {
        List<TableBlock> out = new ArrayList<>();
        for (Object o : list(contract.get("schema"))) {
            if (o instanceof Map<?, ?> object && object.get("context") instanceof Map<?, ?> block) {
                out.add(new TableBlock(string(object.get("name")), block));
            }
        }
        return out;
    }

    /**
     * Joins the tables' blocks, naming each table in the instructions and tagging each entry with
     * the table it came from, so an agent reading the composed block can tell where it applies.
     */
    private static Map<String, Object> compose(List<TableBlock> tables, int statementCap, int constraintCap) {
        List<String> instructions = new ArrayList<>();
        List<Object> statements = new ArrayList<>();
        List<Object> constraints = new ArrayList<>();
        for (TableBlock table : tables) {
            String text = string(table.block().get("instructions"));
            if (text != null && !text.isBlank()) {
                instructions.add(table.table() + ": " + text.strip());
            }
            collect(table, "verifiedStatements", statements, statementCap);
            collect(table, "constraints", constraints, constraintCap);
        }
        Map<String, Object> composed = new LinkedHashMap<>();
        if (!instructions.isEmpty()) {
            composed.put("instructions", String.join("\n", instructions));
        }
        if (!statements.isEmpty()) {
            composed.put("verifiedStatements", statements);
        }
        if (!constraints.isEmpty()) {
            composed.put("constraints", constraints);
        }
        return composed;
    }

    private static void collect(TableBlock table, String key, List<Object> into, int cap) {
        for (Object o : list(table.block().get(key))) {
            if (into.size() >= cap) {
                return;
            }
            if (o instanceof Map<?, ?> entry) {
                Map<String, Object> tagged = new LinkedHashMap<>();
                entry.forEach((k, v) -> tagged.put(String.valueOf(k), v));
                tagged.put("tags", List.of(table.table()));
                into.add(tagged);
            }
        }
    }

    private static List<?> list(Object o) {
        return o instanceof List<?> l ? l : List.of();
    }

    static String string(Object o) {
        return o instanceof String s ? s : o == null ? null : o.toString();
    }
}
