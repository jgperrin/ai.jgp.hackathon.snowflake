package ai.jgp.hackathon.snowflake.catalog;

import java.util.regex.Pattern;

/**
 * Snowflake object names arrive from the browser and end up inside SQL text (identifiers
 * cannot be bind parameters), so every one is checked against a strict pattern and then
 * double-quoted. Anything else is refused rather than escaped.
 */
public final class Identifiers {

    private static final Pattern SAFE = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]{0,254}");

    private Identifiers() {
    }

    public static String require(String name, String what) {
        if (name == null || !SAFE.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid " + what + " name: " + name);
        }
        return name;
    }

    /** Validates then double-quotes, e.g. {@code FINSERV_DW} becomes {@code "FINSERV_DW"}. */
    public static String quote(String name, String what) {
        return '"' + require(name, what) + '"';
    }

    /** Fully qualified, quoted {@code "DB"."SCHEMA"."TABLE"}. */
    public static String qualified(String database, String schema, String table) {
        return quote(database, "database") + '.' + quote(schema, "schema") + '.' + quote(table, "table");
    }
}
