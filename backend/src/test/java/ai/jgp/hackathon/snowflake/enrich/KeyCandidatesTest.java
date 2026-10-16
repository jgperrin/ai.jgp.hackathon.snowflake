package ai.jgp.hackathon.snowflake.enrich;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import ai.jgp.hackathon.snowflake.catalog.CatalogModel.ColumnInfo;
import ai.jgp.hackathon.snowflake.catalog.CatalogModel.TableInfo;
import ai.jgp.hackathon.snowflake.osi.OsiExporter;
import ai.jgp.hackathon.snowflake.product.ContractGenerator;
import ai.jgp.hackathon.snowflake.product.StandardsValidator;
import ai.jgp.hackathon.snowflake.profile.ProfileModel.ColumnProfile;
import ai.jgp.hackathon.snowflake.profile.ProfileModel.TableProfile;
import ai.jgp.hackathon.snowflake.stub.Finserv;

class KeyCandidatesTest {

    static ColumnProfile col(String name, long nulls, long distinct) {
        return new ColumnProfile(name, "NUMBER", nulls, 0, distinct, null, null, null, List.of(), List.of());
    }

    static TableProfile table(String name, ColumnProfile... cols) {
        return new TableProfile(name, 1000L, false, 1000, List.of(cols));
    }

    @Test
    void uniqueNonNullIdentifierIsProposed() {
        TableProfile t = table("CLAIMS", col("STATUS", 0, 5), col("CLAIM_ID", 0, 990), col("POLICY_ID", 0, 1000));
        Proposal p = KeyCandidates.propose("c", t, Map.of("properties", List.of()), new AtomicInteger()).orElseThrow();
        assertThat(p.column()).isEqualTo("CLAIM_ID");
        assertThat(p.kind()).isEqualTo("primaryKey");
        assertThat(p.rationale()).contains("990").contains("1000");
    }

    @Test
    void nothingWhenDuplicatesNullsOrDeclaredKey() {
        assertThat(KeyCandidates.propose("c", table("T", col("A_ID", 0, 500)), null, new AtomicInteger())).isEmpty();
        assertThat(KeyCandidates.propose("c", table("T", col("A_ID", 3, 1000)), null, new AtomicInteger())).isEmpty();
        assertThat(KeyCandidates.propose("c", table("T", col("NAME", 0, 1000)), null, new AtomicInteger())).isEmpty();
        assertThat(KeyCandidates.propose("c", table("T", col("A_ID", 0, 1000)),
                Map.of("properties", List.of(Map.of("name", "X", "primaryKey", true))), new AtomicInteger())).isEmpty();
        assertThat(KeyCandidates.propose("c", new TableProfile("T", 0L, false, 0, List.of(col("A_ID", 0, 0))), null,
                new AtomicInteger())).isEmpty();
    }

    /** Like the live lab: no declared keys. Applying key proposals must give OSI its relationships. */
    @SuppressWarnings("unchecked")
    @Test
    void appliedKeysStayValidAndFeedOsiRelationships() {
        List<Map<String, Object>> contracts = new ArrayList<>();
        Finserv.SCHEMAS.forEach((schema, tables) -> contracts.add(ContractGenerator.contract("FINSERV_DW", schema,
                tables.stream().map(KeyCandidatesTest::withoutKeys).toList(),
                new ContractGenerator.Server("acct", null), Instant.EPOCH)));
        Map<String, Object> product = ContractGenerator.product("FINSERV_DW", contracts, Instant.EPOCH);
        assertThat((List<?>) OsiExporter.export(product, contracts).getOrDefault("relationships", List.of())).isEmpty();

        AtomicInteger ids = new AtomicInteger();
        List<Proposal> keys = new ArrayList<>();
        Finserv.SCHEMAS.forEach((schema, tables) -> tables.forEach(t -> {
            List<ColumnProfile> cols = t.columns().stream()
                    .map(c -> col(c.name(), 0, c.primaryKey() ? 1000 : 10)).toList();
            KeyCandidates.propose("finserv_dw-" + schema.toLowerCase(), new TableProfile(t.name(), 1000L, false, 1000,
                    cols), null, ids).ifPresent(keys::add);
        }));
        List<Map<String, Object>> applied = ProposalApplier.apply(contracts, keys);
        assertThat(new StandardsValidator().validate(product, applied).errors()).isEmpty();
        List<Map<String, Object>> rels = (List<Map<String, Object>>) OsiExporter.export(product, applied)
                .get("relationships");
        assertThat(rels).extracting(r -> r.get("name")).contains("accounts_to_customers", "claims_to_policies");
    }

    static TableInfo withoutKeys(TableInfo t) {
        return new TableInfo(t.name(), t.kind(), t.rowCount(), t.comment(), t.columns().stream()
                .map(c -> new ColumnInfo(c.name(), c.dataType(), c.nullable(), c.comment(), c.ordinal(), false, null,
                        c.numericPrecision(), c.numericScale(), c.maxLength()))
                .toList(), t.foreignKeys());
    }
}
