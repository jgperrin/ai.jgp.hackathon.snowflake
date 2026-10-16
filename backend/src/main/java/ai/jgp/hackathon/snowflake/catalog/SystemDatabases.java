package ai.jgp.hackathon.snowflake.catalog;

import java.util.List;
import java.util.Locale;

/**
 * The one place that decides whether a database is Snowflake's own rather than the account's.
 * The rule is name-based on the SHOW DATABASES {@code name} column (its {@code kind}/{@code origin}
 * columns were not checked against the live account). Changing it is a one-line edit to
 * {@link #PATTERNS}.
 */
public final class SystemDatabases {

    /** Upper-case names; a trailing '*' is a prefix match, anything else is an exact match. */
    static final List<String> PATTERNS = List.of(
            "SNOWFLAKE",
            "SNOWFLAKE_SAMPLE_DATA",
            "ORGDATACLOUD$*", // organization data shares
            "USERS$*"); // per-user scratch database

    private SystemDatabases() {
    }

    public static boolean isSystem(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        String upper = name.toUpperCase(Locale.ROOT);
        for (String pattern : PATTERNS) {
            boolean match = pattern.endsWith("*")
                    ? upper.startsWith(pattern.substring(0, pattern.length() - 1))
                    : upper.equals(pattern);
            if (match) {
                return true;
            }
        }
        return false;
    }
}
