package ai.jgp.hackathon.snowflake.profile;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;

import ai.jgp.hackathon.snowflake.catalog.CatalogModel.ColumnInfo;
import ai.jgp.hackathon.snowflake.catalog.CatalogModel.TableInfo;
import ai.jgp.hackathon.snowflake.catalog.Identifiers;

/**
 * Builds the profiling SQL. Bounded by construction: large tables are read through
 * {@code SAMPLE (n ROWS)}, views through {@code LIMIT n}, and every column is profiled in a
 * single aggregate pass per table instead of one query per column.
 */
final class ProfileSql {

    /** Types MIN/MAX are meaningless or invalid on. */
    private static final Set<String> UNORDERED = Set.of("VARIANT", "OBJECT", "ARRAY", "BINARY", "GEOGRAPHY",
            "GEOMETRY", "VECTOR");

    private ProfileSql() {
    }

    static boolean sampled(TableInfo table, long cap) {
        return "VIEW".equals(table.kind()) || table.rowCount() == null || table.rowCount() > cap;
    }

    /** The FROM source, sampled when the table is larger than {@code cap} rows. */
    static String source(String database, String schema, TableInfo table, long cap) {
        String name = Identifiers.qualified(database, schema, table.name());
        if ("VIEW".equals(table.kind()) || table.rowCount() == null) {
            return "(SELECT * FROM " + name + " LIMIT " + cap + ")";
        }
        return table.rowCount() > cap ? name + " SAMPLE (" + cap + " ROWS)" : name;
    }

    static String column(ColumnInfo c) {
        return Identifiers.quote(c.name(), "column");
    }

    /** Columns aliased C{i}_NULLS, C{i}_DISTINCT, C{i}_MIN, C{i}_MAX, C{i}_LEN; plus N. */
    static String stats(String source, List<ColumnInfo> columns) {
        StringJoiner select = new StringJoiner(", ", "SELECT COUNT(*) AS N, ", " FROM " + source);
        for (int i = 0; i < columns.size(); i++) {
            ColumnInfo c = columns.get(i);
            String col = column(c);
            select.add("COUNT_IF(" + col + " IS NULL) AS C" + i + "_NULLS");
            select.add("APPROX_COUNT_DISTINCT(" + col + ") AS C" + i + "_DISTINCT");
            if (!UNORDERED.contains(c.dataType().toUpperCase(Locale.ROOT))) {
                select.add("MIN(" + col + ")::VARCHAR AS C" + i + "_MIN");
                select.add("MAX(" + col + ")::VARCHAR AS C" + i + "_MAX");
            }
            if (isText(c)) {
                select.add("MAX(LENGTH(" + col + ")) AS C" + i + "_LEN");
            }
        }
        return select.toString();
    }

    /** Top 5 values of each listed column, in one UNION ALL query: rows of (K = column index, V, N). */
    static String topValues(String source, List<ColumnInfo> columns, List<Integer> indexes) {
        StringJoiner union = new StringJoiner(" UNION ALL ");
        for (int i : indexes) {
            String col = column(columns.get(i));
            union.add("(SELECT " + i + " AS K, " + col + "::VARCHAR AS V, COUNT(*) AS N FROM " + source
                    + " WHERE " + col + " IS NOT NULL GROUP BY 2 ORDER BY 3 DESC LIMIT 5)");
        }
        return union.toString();
    }

    static String samples(String database, String schema, TableInfo table) {
        return "SELECT * FROM " + Identifiers.qualified(database, schema, table.name()) + " LIMIT 5";
    }

    static boolean isText(ColumnInfo c) {
        String t = c.dataType().toUpperCase(Locale.ROOT);
        return t.equals("TEXT") || t.startsWith("VARCHAR") || t.equals("STRING") || t.equals("CHAR");
    }
}
