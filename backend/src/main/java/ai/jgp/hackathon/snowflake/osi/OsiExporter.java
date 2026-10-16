package ai.jgp.hackathon.snowflake.osi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import tools.jackson.databind.json.JsonMapper;

/**
 * One-way export: ODPS product + ODCS contracts → one OSI/Apache Ossie semantic model.
 * ODCS/ODPS stay the source of truth; nothing ever reads OSI back. Governance metadata that
 * OSI has no slot for (classification, tags, quality) travels in an ODCS custom extension.
 */
public final class OsiExporter {

    public static final String OSSIE_VERSION = "0.2.0.dev0";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private OsiExporter() {
    }

    private record Source(String database, String schema, Map<?, ?> object) {
    }

    public static Map<String, Object> export(Map<String, Object> product, List<Map<String, Object>> contracts) {
        List<Source> sources = new ArrayList<>();
        for (Map<String, Object> contract : contracts) {
            Map<?, ?> server = list(contract.get("servers")).isEmpty() ? Map.of()
                    : (Map<?, ?>) list(contract.get("servers")).get(0);
            for (Object o : list(contract.get("schema"))) {
                sources.add(new Source(String.valueOf(server.get("database")), String.valueOf(server.get("schema")),
                        (Map<?, ?>) o));
            }
        }
        Map<Source, String> names = datasetNames(sources);

        Map<String, Object> model = new LinkedHashMap<>();
        model.put("version", OSSIE_VERSION);
        model.put("name", String.valueOf(product.getOrDefault("id", "data_product")));
        if (product.get("description") instanceof Map<?, ?> d && d.get("purpose") instanceof String purpose) {
            model.put("description", purpose);
        }
        model.put("ai_context", Map.of("instructions", "Semantic model of the " + product.get("name")
                + " data product. Generated from its ODCS v3.2 data contracts, which remain the source of truth"
                + " for schema, quality rules and SLAs."));
        List<Map<String, Object>> datasets = new ArrayList<>();
        for (Source s : sources) {
            datasets.add(dataset(s, names.get(s)));
        }
        model.put("datasets", datasets);
        List<Map<String, Object>> relationships = relationships(sources, names);
        if (!relationships.isEmpty()) {
            model.put("relationships", relationships);
        }
        return model;
    }

    /** Table names, prefixed with their schema only when two schemas share a table name. */
    static Map<Source, String> datasetNames(List<Source> sources) {
        Map<String, Integer> counts = new HashMap<>();
        sources.forEach(s -> counts.merge(name(s.object()), 1, Integer::sum));
        Map<Source, String> names = new HashMap<>();
        for (Source s : sources) {
            String table = name(s.object());
            names.put(s, counts.get(table) > 1 ? lower(s.schema()) + "_" + table : table);
        }
        return names;
    }

    static Map<String, Object> dataset(Source s, String name) {
        Map<?, ?> o = s.object();
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("name", name);
        d.put("source", s.database() + "." + s.schema() + "." + o.get("physicalName"));
        List<String> pk = primaryKey(o);
        if (!pk.isEmpty()) {
            d.put("primary_key", pk);
        }
        if (o.get("description") instanceof String desc) {
            d.put("description", desc);
        }
        if (o.get("businessName") instanceof String bn) {
            d.put("ai_context", Map.of("synonyms", List.of(bn)));
        }
        List<Map<String, Object>> fields = new ArrayList<>();
        for (Object p : list(o.get("properties"))) {
            fields.add(field((Map<?, ?>) p));
        }
        d.put("fields", fields);
        return d;
    }

    static Map<String, Object> field(Map<?, ?> p) {
        String column = String.valueOf(p.get("physicalName") != null ? p.get("physicalName") : p.get("name"));
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("name", lower(String.valueOf(p.get("name"))));
        f.put("expression", Map.of("dialects", List.of(Map.of("dialect", "ANSI_SQL", "expression", column))));
        f.put("datatype", datatype(p));
        f.put("dimension", Map.of());
        if (p.get("description") instanceof String desc) {
            f.put("description", desc);
        }
        if (p.get("businessName") instanceof String bn) {
            f.put("ai_context", Map.of("synonyms", List.of(bn)));
        }
        Map<String, Object> governance = new LinkedHashMap<>();
        for (String key : List.of("classification", "tags", "quality", "required", "primaryKey")) {
            if (p.get(key) != null) {
                governance.put(key, p.get(key));
            }
        }
        if (governance.containsKey("classification") || governance.containsKey("tags")
                || governance.containsKey("quality")) {
            f.put("custom_extensions", List.of(Map.of("vendor_name", "ODCS",
                    "data", JSON.writeValueAsString(governance))));
        }
        return f;
    }

    static String datatype(Map<?, ?> p) {
        String physical = String.valueOf(p.get("physicalType")).toUpperCase(Locale.ROOT);
        return switch (String.valueOf(p.get("logicalType"))) {
            case "string" -> "String";
            case "integer" -> "Integer";
            case "number" -> physical.startsWith("FLOAT") || physical.startsWith("DOUBLE") || physical.equals("REAL")
                    ? "Float" : "Decimal";
            case "boolean" -> "Boolean";
            case "date" -> "Date";
            case "time" -> "Time";
            // TIMESTAMP_NTZ has no zone; TIMESTAMP_TZ and TIMESTAMP_LTZ identify an instant.
            case "timestamp" -> physical.matches("TIMESTAMP_L?TZ.*") ? "DateTimeTz" : "DateTime";
            default -> "Opaque";
        };
    }

    static List<String> primaryKey(Map<?, ?> object) {
        return list(object.get("properties")).stream().map(p -> (Map<?, ?>) p)
                .filter(p -> Boolean.TRUE.equals(p.get("primaryKey")))
                .sorted(Comparator.comparingInt(p -> p.get("primaryKeyPosition") instanceof Number n ? n.intValue() : 1))
                .map(p -> lower(String.valueOf(p.get("name")))).toList();
    }

    /**
     * Declared foreign keys are rare in Snowflake, so relationships are inferred: a column whose
     * name matches another dataset's single-column primary key points at that dataset.
     */
    static List<Map<String, Object>> relationships(List<Source> sources, Map<Source, String> names) {
        Map<String, Source> byKey = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        for (Source s : sources) {
            List<String> pk = primaryKey(s.object());
            if (pk.size() == 1 && byKey.putIfAbsent(pk.get(0), s) != null) {
                ambiguous.add(pk.get(0));
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Source from : sources) {
            List<String> ownKey = primaryKey(from.object());
            for (Object p : list(from.object().get("properties"))) {
                String column = lower(String.valueOf(((Map<?, ?>) p).get("name")));
                Source to = byKey.get(column);
                if (to == null || to == from || ambiguous.contains(column) || ownKey.equals(List.of(column))) {
                    continue;
                }
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("name", names.get(from) + "_to_" + names.get(to));
                r.put("from", names.get(from));
                r.put("to", names.get(to));
                r.put("from_columns", List.of(column));
                r.put("to_columns", List.of(column));
                r.put("ai_context", "Inferred from matching key names; not a declared constraint.");
                out.add(r);
            }
        }
        return out;
    }

    private static String name(Map<?, ?> object) {
        return lower(String.valueOf(object.get("name")));
    }

    private static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }

    private static List<?> list(Object o) {
        return o instanceof List<?> l ? l : List.of();
    }
}
