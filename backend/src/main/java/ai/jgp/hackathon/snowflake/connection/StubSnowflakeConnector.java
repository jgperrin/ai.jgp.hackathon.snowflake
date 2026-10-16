package ai.jgp.hackathon.snowflake.connection;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.stereotype.Component;

import ai.jgp.hackathon.snowflake.connection.ConnectionModel;

/** Offline: every profile "connects"; nothing under 'local' ever asks for a real connection. */
@Component
@Profile("local")
class StubSnowflakeConnector implements SnowflakeConnector {

    @Override
    public DataSource open(ConnectionModel.Profile profile) {
        return new AbstractDataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                throw new SQLException("No Snowflake under the 'local' profile");
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                return getConnection();
            }
        };
    }

    @Override
    public void close(DataSource dataSource) {
    }
}
