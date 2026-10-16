package ai.jgp.hackathon.snowflake.profile;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

public final class ProfileModel {

    private ProfileModel() {
    }

    /** An empty {@code tables} list profiles every table in the schema. */
    public record ProfileRequest(@NotBlank String database, @NotBlank String schema, List<String> tables) {
    }

    public record SchemaProfile(String database, String schema, List<TableProfile> tables) {
    }

    /**
     * @param sampled    true when the statistics come from a sample, not the whole table
     * @param sampleSize rows actually examined
     */
    public record TableProfile(String table, Long rowCount, boolean sampled, long sampleSize,
            List<ColumnProfile> columns) {
    }

    public record ColumnProfile(String column, String dataType, long nullCount, double nullRatio,
            long distinctCount, String min, String max, Integer maxLength, List<ValueCount> topValues,
            List<String> samples) {
    }

    public record ValueCount(String value, long count) {
    }
}
