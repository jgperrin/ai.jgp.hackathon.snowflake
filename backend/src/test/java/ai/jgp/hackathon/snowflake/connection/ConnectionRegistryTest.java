package ai.jgp.hackathon.snowflake.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.AbstractDataSource;

import ai.jgp.hackathon.snowflake.auth.AuthProperties;
import ai.jgp.hackathon.snowflake.auth.UserContext;
import ai.jgp.hackathon.snowflake.config.CortexProperties;
import ai.jgp.hackathon.snowflake.connection.ConnectionModel.NoConnectionException;
import ai.jgp.hackathon.snowflake.connection.ConnectionModel.Profile;
import ai.jgp.hackathon.snowflake.connection.ConnectionModel.Update;

class ConnectionRegistryTest {

    /** A pool that remembers whose it is. */
    static final class Named extends AbstractDataSource {
        final String owner;

        Named(String owner) {
            this.owner = owner;
        }

        @Override
        public Connection getConnection() {
            throw new UnsupportedOperationException(owner);
        }

        @Override
        public Connection getConnection(String u, String p) {
            return getConnection();
        }
    }

    final List<String> closed = new ArrayList<>();

    final SnowflakeConnector connector = new SnowflakeConnector() {
        @Override
        public DataSource open(Profile p) {
            if ("bad".equals(p.password())) {
                throw new IllegalArgumentException("Snowflake: Incorrect username or password was specified.");
            }
            return new Named(p.user() + ":" + p.password());
        }

        @Override
        public void close(DataSource ds) {
            closed.add(((Named) ds).owner);
        }
    };

    static final CortexProperties NO_SERVER = new CortexProperties(null, null, null, null, null, null, null, "m");
    static final AuthProperties AUTH = new AuthProperties("jgp", "odcs", true);

    @Test
    void eachUserGetsTheirOwnPool() {
        ConnectionRegistry r = new ConnectionRegistry(connector, NO_SERVER, AUTH);
        r.save("alice", new Update("acme-a", "ALICE", "pa", null, null, null));
        r.save("bob", new Update("acme-b", "BOB", "pb", null, null, null));
        UserRoutingDataSource routing = new UserRoutingDataSource(r);
        UserContext.set("alice");
        try {
            assertThatThrownBy(routing::getConnection).hasMessage("ALICE:pa");
            UserContext.set("bob");
            assertThatThrownBy(routing::getConnection).hasMessage("BOB:pb");
        } finally {
            UserContext.clear();
        }
    }

    @Test
    void badCredentialsAreNotSavedAndKeepTheOldConnection() {
        ConnectionRegistry r = new ConnectionRegistry(connector, NO_SERVER, AUTH);
        r.save("alice", new Update("acme", "ALICE", "good", null, null, null));
        assertThatThrownBy(() -> r.save("alice", new Update("acme", "ALICE", "bad", null, null, null)))
                .hasMessageContaining("Incorrect username or password");
        assertThat(r.profile("alice").password()).isEqualTo("good");
        assertThat(((Named) r.dataSource("alice")).owner).isEqualTo("ALICE:good");
    }

    @Test
    void blankPasswordKeepsTheSavedOneAndOldPoolIsClosed() {
        ConnectionRegistry r = new ConnectionRegistry(connector, NO_SERVER, AUTH);
        r.save("alice", new Update("acme", "ALICE", "pw", "WH", null, null));
        r.save("alice", new Update("acme", "ALICE", "", "WH2", "DB", "ROLE"));
        assertThat(r.profile("alice").password()).isEqualTo("pw");
        assertThat(r.view("alice")).satisfies(v -> {
            assertThat(v.warehouse()).isEqualTo("WH2");
            assertThat(v.passwordSet()).isTrue();
            assertThat(v.connected()).isTrue();
        });
        assertThat(closed).containsExactly("ALICE:pw");
    }

    @Test
    void validation() {
        ConnectionRegistry r = new ConnectionRegistry(connector, NO_SERVER, AUTH);
        assertThatThrownBy(() -> r.save("a", new Update("acme", "U", null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> r.save("a", new Update("evil/../host", "U", "p", null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> r.dataSource("nobody")).isInstanceOf(NoConnectionException.class);
        assertThatThrownBy(() -> r.dataSource(null)).isInstanceOf(IllegalStateException.class);
        assertThat(r.view("nobody").connected()).isFalse();
    }

    @Test
    void demoUserIsSeededFromServerSettingsAndOpensLazily() {
        CortexProperties server = new CortexProperties("acme-lab", "USER", "pw", "WH", "DB", null, null, "m");
        ConnectionRegistry r = new ConnectionRegistry(connector, server, AUTH);
        assertThat(r.view("jgp").account()).isEqualTo("acme-lab");
        assertThat(r.view("jgp").connected()).isFalse();
        assertThat(((Named) r.dataSource("jgp")).owner).isEqualTo("USER:pw");
        assertThat(r.view("jgp").connected()).isTrue();
    }

    @Test
    void jdbcUrlCarriesWarehouseAndRole() {
        assertThat(new Profile("acme-lab", "U", "p", "DEFAULT WH", null, "SYSADMIN").jdbcUrl())
                .isEqualTo("jdbc:snowflake://acme-lab.snowflakecomputing.com/?warehouse=DEFAULT+WH&role=SYSADMIN");
    }
}
