package ai.jgp.hackathon.snowflake.connection;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

import javax.sql.DataSource;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import ai.jgp.hackathon.snowflake.connection.ConnectionModel;

@Component
@Profile("!local")
class HikariSnowflakeConnector implements SnowflakeConnector {

    /**
     * The driver is deliberately not set by class name: Snowflake JDBC 4.x moved its public
     * API to net.snowflake.client.api.*, so pinning the old class name breaks on upgrade.
     * Hikari resolves the driver from the jdbc:snowflake:// URL instead.
     */
    @Override
    public DataSource open(ConnectionModel.Profile profile) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("snowflake-" + profile.user());
        config.setJdbcUrl(profile.jdbcUrl());
        config.setUsername(profile.user());
        config.setPassword(profile.password());
        // Arrow, the driver's default result format, needs --add-opens on Java 17+; JSON does not.
        config.addDataSourceProperty("JDBC_QUERY_RESULT_FORMAT", "JSON");
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(0);
        // Keep both well under Snowflake's own idle timeout so the pool retires a connection
        // before the server does, rather than handing out one the server has already closed.
        config.setMaxLifetime(Duration.ofMinutes(50).toMillis());
        config.setKeepaliveTime(Duration.ofMinutes(5).toMillis());
        // Fail fast here: opening is what a "Test & save" in the profile is for.
        config.setInitializationFailTimeout(1);
        HikariDataSource pool;
        try {
            pool = new HikariDataSource(config);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Snowflake: " + rootMessage(e), e);
        }
        try (Connection c = pool.getConnection(); Statement s = c.createStatement();
                ResultSet rs = s.executeQuery("SELECT CURRENT_USER()")) {
            rs.next();
            return pool;
        } catch (SQLException e) {
            pool.close();
            throw new IllegalArgumentException("Snowflake: " + rootMessage(e), e);
        }
    }

    @Override
    public void close(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource h) {
            h.close();
        }
    }

    static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getMessage();
    }
}
