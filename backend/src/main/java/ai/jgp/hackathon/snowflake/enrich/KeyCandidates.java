package ai.jgp.hackathon.snowflake.enrich;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import ai.jgp.hackathon.snowflake.profile.ProfileModel.ColumnProfile;
import ai.jgp.hackathon.snowflake.profile.ProfileModel.TableProfile;

/**
 * Primary keys are rarely declared in Snowflake, so the profile is used as evidence instead:
 * the first column named like an identifier with no nulls and one distinct value per row.
 * Deterministic on purpose: a key is a fact about the data, not something to ask a model about.
 */
final class KeyCandidates {

    /** APPROX_COUNT_DISTINCT is within a few percent; this tolerates its error, not real duplicates. */
    static final double MIN_DISTINCT_RATIO = 0.97;

    private KeyCandidates() {
    }

    static Optional<Proposal> propose(String contractId, TableProfile table, Map<?, ?> schemaObject,
            AtomicInteger ids) {
        if (hasDeclaredKey(schemaObject) || table.sampleSize() == 0) {
            return Optional.empty();
        }
        for (ColumnProfile c : table.columns()) {
            String name = c.column().toUpperCase(Locale.ROOT);
            boolean identifierName = name.equals("ID") || name.endsWith("_ID") || name.endsWith("ID");
            if (identifierName && c.nullCount() == 0
                    && c.distinctCount() >= MIN_DISTINCT_RATIO * table.sampleSize()) {
                return Optional.of(new Proposal("p" + ids.incrementAndGet(), contractId, table.table(), c.column(),
                        Proposal.PRIMARY_KEY, "primary key",
                        "No nulls and ~" + c.distinctCount() + " distinct values in " + table.sampleSize()
                                + " rows examined: unique in the profile."));
            }
        }
        return Optional.empty();
    }

    static boolean hasDeclaredKey(Map<?, ?> schemaObject) {
        return schemaObject != null && schemaObject.get("properties") instanceof java.util.List<?> props
                && props.stream().anyMatch(p -> p instanceof Map<?, ?> m && Boolean.TRUE.equals(m.get("primaryKey")));
    }
}
