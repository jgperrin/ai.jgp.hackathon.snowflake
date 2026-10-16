package ai.jgp.hackathon.snowflake.store;

import java.util.List;

public final class StoreModel {

    private StoreModel() {
    }

    /** One stored document: the product (ODPS), a contract (ODCS) or the semantic export (OSI). */
    public record Artifact(String artifactId, String kind, String name, String version, String status, String yaml,
            String json) {
    }

    public record SaveResult(String id, String version, String savedAt) {
    }

    public record SavedProduct(String id, String name, String version, String savedAt) {
    }

    /** One artifact of a save, without its document: what the store view lists. */
    public record ArtifactSummary(String artifactId, String kind, String name, String version, String status) {
    }

    /** One row of the artifacts table as the browse query reads it: an artifact and the save it belongs to. */
    public record StoredRow(String saveId, String productId, String savedAt, String savedBy, ArtifactSummary artifact) {
    }

    /** One save: name and version come from its ODPS product, artifacts are product first, then contracts, then OSI. */
    public record SavedSave(String saveId, String productId, String name, String version, String savedAt,
            String savedBy, List<ArtifactSummary> artifacts) {
    }
}
