package ai.jgp.hackathon.snowflake.store;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import ai.jgp.hackathon.snowflake.auth.UserContext;
import ai.jgp.hackathon.snowflake.connection.ConnectionModel;
import ai.jgp.hackathon.snowflake.connection.ConnectionRegistry;
import ai.jgp.hackathon.snowflake.store.StoreModel.Artifact;
import ai.jgp.hackathon.snowflake.store.StoreModel.ArtifactSummary;
import ai.jgp.hackathon.snowflake.store.StoreModel.SavedProduct;
import ai.jgp.hackathon.snowflake.store.StoreModel.StoredRow;

@Component
@Profile("!local")
class JdbcArtifactStore implements ArtifactStore {

    private final JdbcTemplate jdbc;
    private final ConnectionRegistry connections;
    /** account|database pairs whose table is known to exist. */
    private final Set<String> ready = ConcurrentHashMap.newKeySet();

    JdbcArtifactStore(JdbcTemplate jdbc, ConnectionRegistry connections) {
        this.jdbc = jdbc;
        this.connections = connections;
    }

    /**
     * The database products are saved to comes from the signed-in user's profile. Idempotent DDL,
     * run on first use rather than at startup so a read-only role can still browse.
     */
    private String ensureTable() {
        ConnectionModel.Profile profile = connections.profile(UserContext.current());
        String database = profile == null ? null : profile.database();
        if (database == null || database.isBlank()) {
            throw new IllegalArgumentException("Set a database in your Snowflake connection to save products");
        }
        if (ready.add(profile.account() + "|" + database)) {
            try {
                jdbc.execute(StoreSql.createSchema(database));
                jdbc.execute(StoreSql.createTable(database));
            } catch (RuntimeException e) {
                ready.remove(profile.account() + "|" + database);
                throw e;
            }
        }
        return database;
    }

    @Override
    public String save(String saveId, String productId, List<Artifact> artifacts) {
        String database = ensureTable();
        String now = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        jdbc.batchUpdate(StoreSql.insert(database), artifacts.stream().map(a -> new Object[] { saveId, productId,
                a.artifactId(), a.kind(), a.name(), a.version(), a.status(), a.yaml(), a.json(), now }).toList());
        return now;
    }

    @Override
    public List<SavedProduct> latest() {
        String database = ensureTable();
        return jdbc.query(StoreSql.latest(database), (rs, i) -> new SavedProduct(rs.getString("SAVE_ID"),
                rs.getString("NAME"), rs.getString("VERSION"), rs.getString("SAVED_AT")));
    }

    @Override
    public List<StoredRow> saves() {
        String database = ensureTable();
        return jdbc.query(StoreSql.saves(database), (rs, i) -> new StoredRow(rs.getString("SAVE_ID"),
                rs.getString("PRODUCT_ID"), rs.getString("SAVED_AT"), rs.getString("SAVED_BY"),
                new ArtifactSummary(rs.getString("ARTIFACT_ID"), rs.getString("KIND"), rs.getString("NAME"),
                        rs.getString("VERSION"), rs.getString("STATUS"))));
    }

    @Override
    public List<Artifact> load(String saveId) {
        String database = ensureTable();
        return jdbc.query(StoreSql.load(database), (rs, i) -> new Artifact(rs.getString("ARTIFACT_ID"),
                rs.getString("KIND"), rs.getString("NAME"), rs.getString("VERSION"), rs.getString("STATUS"),
                rs.getString("YAML"), rs.getString("DOC")), saveId);
    }

    @Override
    public int delete(String saveId) {
        String database = ensureTable();
        return jdbc.update(StoreSql.delete(database), saveId);
    }
}
