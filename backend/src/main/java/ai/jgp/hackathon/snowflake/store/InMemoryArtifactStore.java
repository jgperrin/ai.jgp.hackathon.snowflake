package ai.jgp.hackathon.snowflake.store;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import ai.jgp.hackathon.snowflake.auth.UserContext;
import ai.jgp.hackathon.snowflake.store.StoreModel.Artifact;
import ai.jgp.hackathon.snowflake.store.StoreModel.ArtifactSummary;
import ai.jgp.hackathon.snowflake.store.StoreModel.SavedProduct;
import ai.jgp.hackathon.snowflake.store.StoreModel.StoredRow;

/** Offline stand-in for the Snowflake table; same behaviour, lost on restart. */
@Component
@Profile("local")
class InMemoryArtifactStore implements ArtifactStore {

    private record Save(String productId, String savedAt, String savedBy, List<Artifact> artifacts) {
    }

    private final Map<String, Save> saves = new ConcurrentHashMap<>();

    @Override
    public String save(String saveId, String productId, List<Artifact> artifacts) {
        String now = Instant.now().truncatedTo(ChronoUnit.MILLIS).toString();
        saves.put(saveId, new Save(productId, now, UserContext.current(), List.copyOf(artifacts)));
        return now;
    }

    @Override
    public List<SavedProduct> latest() {
        Map<String, SavedProduct> latest = new LinkedHashMap<>();
        saves.entrySet().stream().sorted(Comparator.comparing((Map.Entry<String, Save> e) -> e.getValue().savedAt())
                .reversed()).forEach(e -> {
                    Artifact product = e.getValue().artifacts().get(0);
                    latest.putIfAbsent(e.getValue().productId(), new SavedProduct(e.getKey(), product.name(),
                            product.version(), e.getValue().savedAt()));
                });
        return new ArrayList<>(latest.values());
    }

    @Override
    public List<StoredRow> saves() {
        return saves.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<String, Save> e) -> e.getValue().savedAt()).reversed())
                .flatMap(e -> e.getValue().artifacts().stream().map(a -> new StoredRow(e.getKey(),
                        e.getValue().productId(), e.getValue().savedAt(), e.getValue().savedBy(),
                        new ArtifactSummary(a.artifactId(), a.kind(), a.name(), a.version(), a.status()))))
                .limit(200).toList();
    }

    @Override
    public List<Artifact> load(String saveId) {
        Save save = saves.get(saveId);
        return save == null ? List.of() : save.artifacts();
    }

    @Override
    public int delete(String saveId) {
        Save removed = saves.remove(saveId);
        return removed == null ? 0 : removed.artifacts().size();
    }
}
