package ai.jgp.hackathon.snowflake.quality;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import ai.jgp.hackathon.snowflake.catalog.Identifiers;
import ai.jgp.hackathon.snowflake.enrich.QualityRules;

/**
 * Compiles an ODCS quality object into one SQL statement returning a single number, and judges
 * that number against the rule's operator. Library metrics count violations (or rows, for
 * rowCount); SQL rules are run as written once {@code ${table}} and {@code ${column}} are filled in.
 */
public final class RuleCompiler {

    private RuleCompiler() {
    }

    public static String sql(Map<?, ?> rule, String database, String schema, String table, String column) {
        String from = Identifiers.qualified(database, schema, table);
        String col = column == null ? null : Identifiers.quote(column, "column");
        String type = String.valueOf(rule.get("type"));
        if ("sql".equals(type)) {
            String query = String.valueOf(rule.get("query"));
            if (!QualityRules.isReadOnlySelect(query)) {
                throw new IllegalArgumentException("Refused: SQL quality rules must be a single read-only SELECT");
            }
            query = query.replace("${table}", from).replace("{table}", from);
            if (col != null) {
                query = query.replace("${column}", col).replace("{column}", col);
            }
            return query.strip().replaceAll(";$", "");
        }
        String metric = String.valueOf(rule.get("metric"));
        if (!"rowCount".equals(metric) && col == null) {
            throw new IllegalArgumentException("Metric " + metric + " needs a column");
        }
        return switch (metric) {
            case "rowCount" -> "SELECT COUNT(*) FROM " + from;
            case "nullValues" -> "SELECT COUNT(*) FROM " + from + " WHERE " + col + " IS NULL";
            case "missingValues" -> "SELECT COUNT(*) FROM " + from + " WHERE " + col + " IS NULL OR TRIM("
                    + col + "::VARCHAR) = ''";
            case "duplicateValues" -> "SELECT COUNT(" + col + ") - COUNT(DISTINCT " + col + ") FROM " + from;
            case "invalidValues" -> "SELECT COUNT(*) FROM " + from + " WHERE " + col + " IS NOT NULL AND NOT ("
                    + validity(rule, col) + ")";
            default -> throw new IllegalArgumentException("Unsupported metric " + metric);
        };
    }

    private static String validity(Map<?, ?> rule, String col) {
        if (!(rule.get("arguments") instanceof Map<?, ?> args)) {
            throw new IllegalArgumentException("invalidValues needs arguments.validValues or arguments.pattern");
        }
        if (args.get("validValues") instanceof List<?> values && !values.isEmpty()) {
            StringBuilder in = new StringBuilder(col + "::VARCHAR IN (");
            for (int i = 0; i < values.size(); i++) {
                in.append(i == 0 ? "" : ", ").append(literal(String.valueOf(values.get(i))));
            }
            return in.append(')').toString();
        }
        if (args.get("pattern") instanceof String pattern) {
            return "REGEXP_LIKE(" + col + "::VARCHAR, " + literal(pattern) + ")";
        }
        throw new IllegalArgumentException("invalidValues needs arguments.validValues or arguments.pattern");
    }

    /** A single-quoted SQL string literal with quotes and backslashes escaped. */
    static String literal(String s) {
        return "'" + s.replace("\\", "\\\\").replace("'", "''") + "'";
    }

    /** Human-readable form of the operator, e.g. "= 0" or "between 10 and 100". */
    public static String threshold(Map<?, ?> rule) {
        for (String op : List.of("mustBe", "mustNotBe", "mustBeGreaterThan", "mustBeGreaterOrEqualTo",
                "mustBeLessThan", "mustBeLessOrEqualTo", "mustBeBetween", "mustNotBeBetween")) {
            Object v = rule.get(op);
            if (v == null) {
                continue;
            }
            return switch (op) {
                case "mustBe" -> "= " + v;
                case "mustNotBe" -> "≠ " + v;
                case "mustBeGreaterThan" -> "> " + v;
                case "mustBeGreaterOrEqualTo" -> "≥ " + v;
                case "mustBeLessThan" -> "< " + v;
                case "mustBeLessOrEqualTo" -> "≤ " + v;
                case "mustBeBetween" -> "between " + ((List<?>) v).get(0) + " and " + ((List<?>) v).get(1);
                default -> "not between " + ((List<?>) v).get(0) + " and " + ((List<?>) v).get(1);
            };
        }
        return "= 0";
    }

    public static boolean passes(Map<?, ?> rule, double observed) {
        for (String op : List.of("mustBe", "mustNotBe", "mustBeGreaterThan", "mustBeGreaterOrEqualTo",
                "mustBeLessThan", "mustBeLessOrEqualTo", "mustBeBetween", "mustNotBeBetween")) {
            Object v = rule.get(op);
            if (v == null) {
                continue;
            }
            if (v instanceof List<?> range) {
                double lo = num(range.get(0));
                double hi = num(range.get(1));
                boolean inside = observed >= lo && observed <= hi;
                return op.equals("mustBeBetween") == inside;
            }
            double t = num(v);
            return switch (op) {
                case "mustBe" -> observed == t;
                case "mustNotBe" -> observed != t;
                case "mustBeGreaterThan" -> observed > t;
                case "mustBeGreaterOrEqualTo" -> observed >= t;
                case "mustBeLessThan" -> observed < t;
                default -> observed <= t;
            };
        }
        return observed == 0;
    }

    private static double num(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(o).toLowerCase(Locale.ROOT));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Threshold is not a number: " + o);
        }
    }
}
