package ai.jgp.hackathon.snowflake.enrich;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns whatever the model wrote into an ODCS v3.2 quality object, or nothing. Only known fields
 * survive, exactly one comparison operator is kept (the schema's oneOf demands it), and SQL rules
 * must be a single SELECT so that executing them later can never change data.
 */
public final class QualityRules {

    static final List<String> OPERATORS = List.of("mustBe", "mustNotBe", "mustBeGreaterThan",
            "mustBeGreaterOrEqualTo", "mustBeLessThan", "mustBeLessOrEqualTo", "mustBeBetween", "mustNotBeBetween");
    static final Set<String> METRICS = Set.of("nullValues", "missingValues", "invalidValues", "duplicateValues",
            "rowCount");
    static final Set<String> DIMENSIONS = Set.of("accuracy", "completeness", "conformity", "consistency", "coverage",
            "timeliness", "uniqueness");
    private static final Pattern WRITE = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|MERGE|DROP|CREATE|ALTER|TRUNCATE|GRANT|REVOKE|CALL|PUT|COPY|EXECUTE|USE)\\b",
            Pattern.CASE_INSENSITIVE);

    private QualityRules() {
    }

    /** True when {@code query} is one read-only SELECT (a trailing semicolon is tolerated). */
    public static boolean isReadOnlySelect(String query) {
        if (query == null) {
            return false;
        }
        String q = query.strip();
        if (q.endsWith(";")) {
            q = q.substring(0, q.length() - 1);
        }
        String upper = q.toUpperCase(Locale.ROOT);
        return (upper.startsWith("SELECT") || upper.startsWith("WITH")) && !q.contains(";") && !WRITE.matcher(q).find();
    }

    /** Returns a clean quality object, or null when the input cannot be made valid. */
    public static Map<String, Object> sanitize(Map<?, ?> raw) {
        Map<String, Object> rule = new LinkedHashMap<>();
        String type = string(raw.get("type"));
        String metric = string(raw.get("metric"));
        if (type == null && metric != null) {
            type = "library";
        }
        if ("library".equals(type)) {
            if (!METRICS.contains(metric)) {
                return null;
            }
            rule.put("type", "library");
            rule.put("metric", metric);
            if (raw.get("arguments") instanceof Map<?, ?> args) {
                Map<String, Object> clean = new LinkedHashMap<>();
                if (args.get("validValues") instanceof List<?> values && !values.isEmpty()) {
                    clean.put("validValues", values);
                }
                if (args.get("pattern") instanceof String pattern && !pattern.isBlank()) {
                    clean.put("pattern", pattern);
                }
                if (!clean.isEmpty()) {
                    rule.put("arguments", clean);
                }
            }
            if ("invalidValues".equals(metric) && !rule.containsKey("arguments")) {
                return null;
            }
        } else if ("sql".equals(type)) {
            String query = string(raw.get("query"));
            if (!isReadOnlySelect(query)) {
                return null;
            }
            rule.put("type", "sql");
            rule.put("query", query.strip().replaceAll(";$", ""));
        } else {
            return null;
        }
        if (!putOperator(raw, rule)) {
            return null;
        }
        copyString(raw, rule, "description");
        String dimension = string(raw.get("dimension"));
        if (dimension != null && DIMENSIONS.contains(dimension.toLowerCase(Locale.ROOT))) {
            rule.put("dimension", dimension.toLowerCase(Locale.ROOT));
        }
        copyString(raw, rule, "severity");
        copyString(raw, rule, "unit");
        return rule;
    }

    private static boolean putOperator(Map<?, ?> raw, Map<String, Object> rule) {
        for (String op : OPERATORS) {
            Object v = raw.get(op);
            if (v == null) {
                continue;
            }
            if (op.endsWith("Between")) {
                if (v instanceof List<?> l && l.size() == 2 && l.get(0) instanceof Number a && l.get(1) instanceof Number b
                        && a.doubleValue() < b.doubleValue()) {
                    rule.put(op, List.of(a, b));
                    return true;
                }
                return false;
            }
            if (op.startsWith("mustBeGreater") || op.startsWith("mustBeLess")) {
                if (!(v instanceof Number)) {
                    return false;
                }
            }
            rule.put(op, v);
            return true;
        }
        // No operator: the conventional meaning of a violation count is "none".
        rule.put("mustBe", 0);
        return !"rowCount".equals(rule.get("metric"));
    }

    private static void copyString(Map<?, ?> from, Map<String, Object> to, String key) {
        String v = string(from.get(key));
        if (v != null && !v.isBlank()) {
            to.put(key, v);
        }
    }

    static String string(Object o) {
        return o instanceof String s ? s : o == null ? null : o.toString();
    }
}
