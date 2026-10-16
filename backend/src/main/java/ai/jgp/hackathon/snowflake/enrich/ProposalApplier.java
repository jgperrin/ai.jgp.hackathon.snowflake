package ai.jgp.hackathon.snowflake.enrich;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ai.jgp.hackathon.snowflake.product.Bundle;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Writes accepted proposals into a copy of the contracts. Only what is passed in is applied. */
public final class ProposalApplier {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ProposalApplier() {
    }

    public static List<Map<String, Object>> apply(List<Map<String, Object>> contracts, List<Proposal> accepted) {
        List<Map<String, Object>> copy = JSON.convertValue(contracts, new TypeReference<>() {
        });
        for (Proposal p : accepted) {
            Map<String, Object> contract = copy.stream().filter(c -> p.contractId().equals(c.get("id"))).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown contract " + p.contractId()));
            if (Proposal.SLA.equals(p.kind())) {
                list(contract, "slaProperties").add(p.value());
                continue;
            }
            Map<String, Object> object = find(list(contract, "schema"), p.table(), "table");
            Map<String, Object> target = p.column() == null ? object : find(list(object, "properties"), p.column(),
                    "column");
            switch (p.kind()) {
                case Proposal.DESCRIPTION, Proposal.BUSINESS_NAME -> target.put(p.kind(), String.valueOf(p.value()));
                case Proposal.CLASSIFICATION -> {
                    if (p.column() == null) {
                        throw new IllegalArgumentException("Classification applies to columns only");
                    }
                    target.put("classification", String.valueOf(p.value()));
                }
                case Proposal.TAG -> {
                    List<Object> tags = list(target, "tags");
                    if (!tags.contains(p.value())) {
                        tags.add(String.valueOf(p.value()));
                    }
                }
                case Proposal.QUALITY_RULE -> {
                    if (!(p.value() instanceof Map<?, ?> raw)) {
                        throw new IllegalArgumentException("Quality rule must be an object");
                    }
                    Map<String, Object> rule = QualityRules.sanitize(raw);
                    if (rule == null) {
                        throw new IllegalArgumentException("Quality rule is not valid ODCS or not read-only");
                    }
                    list(target, "quality").add(rule);
                }
                default -> throw new IllegalArgumentException("Unknown proposal kind " + p.kind());
            }
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    static List<Object> list(Map<String, Object> parent, String key) {
        Object v = parent.get(key);
        if (v instanceof List<?> l) {
            return (List<Object>) l;
        }
        List<Object> l = new ArrayList<>();
        parent.put(key, l);
        return l;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> find(List<Object> items, String name, String what) {
        return items.stream().filter(i -> i instanceof Map<?, ?> m && name.equals(m.get("name")))
                .map(i -> (Map<String, Object>) i).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown " + what + " " + name));
    }

    public record ApplyRequest(Bundle bundle, List<Proposal> accepted) {
    }
}
