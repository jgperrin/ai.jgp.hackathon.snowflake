package ai.jgp.hackathon.snowflake.connection;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import ai.jgp.hackathon.snowflake.auth.AuthProperties;
import ai.jgp.hackathon.snowflake.config.CortexProperties;
import ai.jgp.hackathon.snowflake.connection.ConnectionModel.Defaults;
import ai.jgp.hackathon.snowflake.connection.ConnectionModel.NoConnectionException;
import ai.jgp.hackathon.snowflake.connection.ConnectionModel.Profile;
import ai.jgp.hackathon.snowflake.connection.ConnectionModel.Update;
import ai.jgp.hackathon.snowflake.connection.ConnectionModel.View;

/**
 * Each user's Snowflake profile and connection pool, in memory. The demo user's profile is
 * seeded from the server's SNOWFLAKE_* settings when they are present, so judges can start
 * without credentials of their own.
 */
@Component
public class ConnectionRegistry {

    private static final Logger log = LoggerFactory.getLogger(ConnectionRegistry.class);

    private final SnowflakeConnector connector;
    private final Map<String, Profile> profiles = new ConcurrentHashMap<>();
    private final Map<String, DataSource> pools = new ConcurrentHashMap<>();
    private final Map<String, String> errors = new ConcurrentHashMap<>();
    private final Defaults defaults;

    ConnectionRegistry(SnowflakeConnector connector, CortexProperties server, AuthProperties auth) {
        this.connector = connector;
        this.defaults = new Defaults(blank(server.account()), blank(server.user()), blank(server.warehouse()),
                blank(server.database()), blank(server.role()));
        if (server.account() != null && !server.account().isBlank() && auth.demoUser() != null) {
            profiles.put(auth.demoUser(), new Profile(trim(server.account()), trim(server.user()),
                    server.password() == null || server.password().isEmpty() ? null : server.password(),
                    trim(server.warehouse()), trim(server.database()), trim(server.role())));
            log.info("Demo user's Snowflake profile seeded from server settings (account {})", server.account());
        }
    }

    public View view(String user) {
        Profile p = profiles.get(user);
        return p == null ? View.empty() : p.view(pools.containsKey(user), errors.get(user));
    }

    /** What the server's settings would prefill; the password is not part of it. */
    public Defaults defaults() {
        return defaults;
    }

    /** The profile of the current user, or null. */
    public Profile profile(String user) {
        return user == null ? null : profiles.get(user);
    }

    /** Tests the new settings first; only a working connection replaces the old one. */
    public View save(String user, Update update) {
        Profile old = profiles.get(user);
        String password = update.password() == null || update.password().isEmpty()
                ? (old == null ? null : old.password()) : update.password();
        Profile next = new Profile(trim(update.account()), trim(update.user()), password, trim(update.warehouse()),
                trim(update.database()), trim(update.role()));
        if (next.account() == null || next.user() == null || next.password() == null) {
            throw new IllegalArgumentException("Account, user and password are required");
        }
        if (!next.account().matches("[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("Account should look like orgname-accountname or xy12345.us-east-1");
        }
        DataSource fresh = connector.open(next);
        profiles.put(user, next);
        errors.remove(user);
        DataSource previous = pools.put(user, fresh);
        if (previous != null) {
            connector.close(previous);
        }
        return view(user);
    }

    /** The user's pool, opened on first use from a seeded profile. */
    public DataSource dataSource(String user) {
        if (user == null) {
            throw new IllegalStateException("No signed-in user on this thread");
        }
        DataSource existing = pools.get(user);
        if (existing != null) {
            return existing;
        }
        Profile p = profiles.get(user);
        if (p == null) {
            throw new NoConnectionException();
        }
        synchronized (this) {
            existing = pools.get(user);
            if (existing != null) {
                return existing;
            }
            try {
                DataSource opened = connector.open(p);
                pools.put(user, opened);
                errors.remove(user);
                return opened;
            } catch (RuntimeException e) {
                errors.put(user, e.getMessage());
                throw e;
            }
        }
    }

    private static String blank(String s) {
        String t = trim(s);
        return t == null ? "" : t;
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
