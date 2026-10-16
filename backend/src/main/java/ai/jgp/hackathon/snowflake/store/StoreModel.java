package ai.jgp.hackathon.snowflake.store;

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
}
