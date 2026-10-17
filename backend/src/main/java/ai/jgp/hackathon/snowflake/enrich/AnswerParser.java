package ai.jgp.hackathon.snowflake.enrich;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import tools.jackson.core.JacksonException;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the model's answer defensively. A malformed answer yields no proposals and a warning,
 * never an exception; anything referring to a column that does not exist is dropped.
 */
final class AnswerParser {

    static final Set<String> CLASSIFICATIONS = Set.of("public", "internal", "confidential", "restricted");
    static final Set<String> SLA_PROPERTIES = Set.of("freshness", "latency", "retention", "frequency",
            "availability", "throughput", "errorRate", "generalAvailability", "endOfSupport", "endOfLife",
            "timeOfAvailability");
    /** Models drift from strict JSON in small ways; accept those rather than lose a table. */
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA, JsonReadFeature.ALLOW_JAVA_COMMENTS,
                    JsonReadFeature.ALLOW_SINGLE_QUOTES, JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
            .build();

    record Result(List<Proposal> proposals, List<String> warnings) {
    }

    private AnswerParser() {
    }

    static Result parse(String contractId, String table, Set<String> columns, String answer, AtomicInteger ids) {
        List<String> warnings = new ArrayList<>();
        Map<?, ?> root = json(answer);
        if (root == null) {
            warnings.add(table + ": Cortex answer was not valid JSON; no proposals for this table.");
            return new Result(List.of(), warnings);
        }
        List<Proposal> out = new ArrayList<>();
        if (root.get("table") instanceof Map<?, ?> t) {
            text(t.get("description")).ifPresent(v -> out.add(p(ids, contractId, table, null, Proposal.DESCRIPTION, v,
                    "Inferred from the table name, columns and profile.")));
            text(t.get("businessName")).ifPresent(v -> out.add(p(ids, contractId, table, null,
                    Proposal.BUSINESS_NAME, v, "Human-friendly name for catalogs and BI tools.")));
        }
        if (root.get("columns") instanceof List<?> cols) {
            for (Object o : cols) {
                if (!(o instanceof Map<?, ?> c)) {
                    continue;
                }
                String name = QualityRules.string(c.get("name"));
                if (name == null || !columns.contains(name)) {
                    warnings.add(table + ": ignored proposal for unknown column " + name);
                    continue;
                }
                text(c.get("description")).ifPresent(v -> out.add(p(ids, contractId, table, name,
                        Proposal.DESCRIPTION, v, "Inferred from the column name, type and values.")));
                text(c.get("businessName")).ifPresent(v -> out.add(p(ids, contractId, table, name,
                        Proposal.BUSINESS_NAME, v, "Human-friendly name.")));
                text(c.get("classification")).map(v -> v.toLowerCase(Locale.ROOT)).filter(CLASSIFICATIONS::contains)
                        .ifPresent(v -> out.add(p(ids, contractId, table, name, Proposal.CLASSIFICATION, v,
                                Boolean.TRUE.equals(c.get("pii")) ? "Contains personal data (PII)."
                                        : "Sensitivity inferred from content.")));
                List<String> tags = new ArrayList<>();
                if (Boolean.TRUE.equals(c.get("pii"))) {
                    tags.add("pii");
                }
                if (c.get("tags") instanceof List<?> l) {
                    l.forEach(x -> text(x).map(s -> s.toLowerCase(Locale.ROOT).replace(' ', '_'))
                            .filter(s -> !tags.contains(s)).ifPresent(tags::add));
                }
                tags.forEach(tag -> out.add(p(ids, contractId, table, name, Proposal.TAG, tag,
                        "pii".equals(tag) ? "Personal data: restrict access and mask downstream." : "Topic tag.")));
            }
        }
        if (root.get("qualityRules") instanceof List<?> rules) {
            for (Object o : rules) {
                if (!(o instanceof Map<?, ?> r)) {
                    continue;
                }
                String column = QualityRules.string(r.get("column"));
                if (column != null && (column.isBlank() || "null".equals(column))) {
                    column = null;
                }
                if (column != null && !columns.contains(column)) {
                    warnings.add(table + ": ignored rule on unknown column " + column);
                    continue;
                }
                Map<String, Object> rule = QualityRules.sanitize(r);
                if (rule == null) {
                    warnings.add(table + ": ignored a rule that is not valid ODCS or not read-only.");
                    continue;
                }
                out.add(p(ids, contractId, table, column, Proposal.QUALITY_RULE, rule,
                        text(r.get("rationale")).orElse("Holds on the profiled data.")));
            }
        }
        if (root.get("sla") instanceof List<?> slas) {
            for (Object o : slas) {
                if (o instanceof Map<?, ?> s) {
                    Map<String, Object> sla = sla(s);
                    if (sla != null) {
                        out.add(p(ids, contractId, table, null, Proposal.SLA, sla,
                                text(s.get("rationale")).orElse("Typical for this kind of data.")));
                    }
                }
            }
        }
        if (root.get("context") instanceof Map<?, ?> raw) {
            Map<String, Object> block = ContextBlocks.sanitize(raw);
            if (block == null) {
                warnings.add(table + ": ignored an empty context block.");
            } else {
                out.add(p(ids, contractId, table, null, Proposal.CONTEXT, block,
                        text(raw.get("rationale")).orElse("Guidance for AI agents reading this table.")));
            }
        }
        return new Result(out, warnings);
    }

    static Map<String, Object> sla(Map<?, ?> s) {
        String property = QualityRules.string(s.get("property"));
        Object value = s.get("value");
        if (property == null || !SLA_PROPERTIES.contains(property)
                || !(value instanceof String || value instanceof Number || value instanceof Boolean)) {
            return null;
        }
        Map<String, Object> sla = new LinkedHashMap<>();
        sla.put("property", property);
        sla.put("value", value);
        text(s.get("unit")).ifPresent(v -> sla.put("unit", v));
        text(s.get("element")).ifPresent(v -> sla.put("element", v));
        text(s.get("description")).ifPresent(v -> sla.put("description", v));
        return sla;
    }

    /** Tolerates markdown fences and chatter around the JSON object. */
    static Map<?, ?> json(String answer) {
        if (answer == null) {
            return null;
        }
        int start = answer.indexOf('{');
        int end = answer.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return JSON.readValue(answer.substring(start, end + 1), Map.class);
        } catch (JacksonException e) {
            return null;
        }
    }

    private static java.util.Optional<String> text(Object o) {
        return o instanceof String s && !s.isBlank() ? java.util.Optional.of(s.strip()) : java.util.Optional.empty();
    }

    private static Proposal p(AtomicInteger ids, String contractId, String table, String column, String kind,
            Object value, String rationale) {
        return new Proposal("p" + ids.incrementAndGet(), contractId, table, column, kind, value, rationale);
    }
}
