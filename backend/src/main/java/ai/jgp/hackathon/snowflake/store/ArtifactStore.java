package ai.jgp.hackathon.snowflake.store;

import java.util.List;

import ai.jgp.hackathon.snowflake.store.StoreModel.Artifact;
import ai.jgp.hackathon.snowflake.store.StoreModel.SavedProduct;

/** Append-only: every save adds rows under a new save id, so history is never overwritten. */
public interface ArtifactStore {

    /** Stores all artifacts of one save; returns the ISO timestamp of the save. */
    String save(String saveId, String productId, List<Artifact> artifacts);

    List<SavedProduct> latest();

    /** All artifacts of one save, product first. */
    List<Artifact> load(String saveId);
}
