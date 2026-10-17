package ai.jgp.hackathon.snowflake.store;

import java.util.List;

import ai.jgp.hackathon.snowflake.store.StoreModel.Artifact;
import ai.jgp.hackathon.snowflake.store.StoreModel.SavedProduct;
import ai.jgp.hackathon.snowflake.store.StoreModel.StoredRow;

/**
 * Every save adds rows under a new save id, so a stored document is never overwritten. A save can
 * be deleted whole (DIND#111) — that is a user asking for it, not a rewrite.
 */
public interface ArtifactStore {

    /** Stores all artifacts of one save; returns the ISO timestamp of the save. */
    String save(String saveId, String productId, List<Artifact> artifacts);

    List<SavedProduct> latest();

    /** Every artifact row of the newest saves, any kind, newest save first; bounded. */
    List<StoredRow> saves();

    /** All artifacts of one save, product first. */
    List<Artifact> load(String saveId);

    /** Deletes every artifact row of one save; returns how many rows went. */
    int delete(String saveId);
}
