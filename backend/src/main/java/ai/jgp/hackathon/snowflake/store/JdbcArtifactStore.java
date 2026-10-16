package ai.jgp.hackathon.snowflake.store;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import ai.jgp.hackathon.snowflake.store.StoreModel.Artifact;
import ai.jgp.hackathon.snowflake.store.StoreModel.SavedProduct;

@Component
@Profile("!local")
class JdbcArtifactStore implements ArtifactStore {

    private final JdbcTemplate jdbc;
    private final String database;
    private volatile boolean ready;

    JdbcArtifactStore(JdbcTemplate jdbc, @Value("${storage.database:${snowflake.database:}}") String database) {
        this.jdbc = jdbc;
        this.database = database;
    }

    /** Idempotent DDL, run once on first use rather than at startup so a read-only role can still browse. */
    private void ensureTable() {
        if (ready) {
            return;
        }
        synchronized (this) {
            if (database == null || database.isBlank()) {
                throw new IllegalStateException("Set STORAGE_DATABASE or SNOWFLAKE_DATABASE to store products");
            }
            jdbc.execute(StoreSql.createSchema(database));
            jdbc.execute(StoreSql.createTable(database));
            ready = true;
        }
    }

    @Override
    public String save(String saveId, String productId, List<Artifact> artifacts) {
        ensureTable();
        String now = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        jdbc.batchUpdate(StoreSql.insert(database), artifacts.stream().map(a -> new Object[] { saveId, productId,
                a.artifactId(), a.kind(), a.name(), a.version(), a.status(), a.yaml(), a.json(), now }).toList());
        return now;
    }

    @Override
    public List<SavedProduct> latest() {
        ensureTable();
        return jdbc.query(StoreSql.latest(database), (rs, i) -> new SavedProduct(rs.getString("SAVE_ID"),
                rs.getString("NAME"), rs.getString("VERSION"), rs.getString("SAVED_AT")));
    }

    @Override
    public List<Artifact> load(String saveId) {
        ensureTable();
        return jdbc.query(StoreSql.load(database), (rs, i) -> new Artifact(rs.getString("ARTIFACT_ID"),
                rs.getString("KIND"), rs.getString("NAME"), rs.getString("VERSION"), rs.getString("STATUS"),
                rs.getString("YAML"), rs.getString("DOC")), saveId);
    }
}
