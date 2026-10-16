package ai.jgp.hackathon.snowflake.connection;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class ConnectionModel {

    private ConnectionModel() {
    }

    /** A user's Snowflake connection. Held server-side only; {@link #password} never leaves the backend. */
    public record Profile(String account, String user, String password, String warehouse, String database,
            String role) {

        public String jdbcUrl() {
            StringBuilder query = new StringBuilder();
            append(query, "warehouse", warehouse);
            append(query, "role", role);
            String url = "jdbc:snowflake://" + account + ".snowflakecomputing.com/";
            return query.isEmpty() ? url : url + "?" + query;
        }

        private static void append(StringBuilder query, String name, String value) {
            if (value != null && !value.isBlank()) {
                query.append(query.isEmpty() ? "" : "&").append(name).append('=')
                        .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
            }
        }

        public View view(boolean connected, String error) {
            return new View(account, user, warehouse, database, role, password != null && !password.isEmpty(),
                    connected, error);
        }
    }

    /** What the browser sees: everything except the password. */
    public record View(String account, String user, String warehouse, String database, String role,
            boolean passwordSet, boolean connected, String error) {

        static View empty() {
            return new View(null, null, null, null, null, false, false, null);
        }
    }

    /** {@code password} empty or absent keeps the saved one. */
    public record Update(String account, String user, String password, String warehouse, String database,
            String role) {
    }

    /** Signed in, but no Snowflake connection yet: the UI opens the profile panel on this. */
    public static class NoConnectionException extends RuntimeException {

        public NoConnectionException() {
            super("Set up your Snowflake connection in your profile first.");
        }
    }
}
