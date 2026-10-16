package ai.jgp.hackathon.snowflake.connection;

import java.sql.Connection;
import java.sql.SQLException;

import org.springframework.jdbc.datasource.AbstractDataSource;

import ai.jgp.hackathon.snowflake.auth.UserContext;

/** The one DataSource the app sees; each call is routed to the signed-in user's own pool. */
public class UserRoutingDataSource extends AbstractDataSource {

    private final ConnectionRegistry registry;

    public UserRoutingDataSource(ConnectionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return registry.dataSource(UserContext.current()).getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        throw new SQLException("Per-user pools only; credentials come from the user's profile");
    }
}
