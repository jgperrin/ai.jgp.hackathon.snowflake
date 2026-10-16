package ai.jgp.hackathon.snowflake.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import ai.jgp.hackathon.snowflake.catalog.CatalogModel.TableInfo;
import ai.jgp.hackathon.snowflake.stub.Finserv;

class ProfileSqlTest {

    final TableInfo claims = Finserv.SCHEMAS.get("INSURANCE").get(1);
    final TableInfo customers = Finserv.SCHEMAS.get("CORE").get(1);
    final TableInfo view = Finserv.SCHEMAS.get("ANALYTICS").get(0);

    @Test
    void smallTableIsReadWhole() {
        assertThat(ProfileSql.source("FINSERV_DW", "INSURANCE", claims, 100_000))
                .isEqualTo("\"FINSERV_DW\".\"INSURANCE\".\"CLAIMS\"");
        assertThat(ProfileSql.sampled(claims, 100_000)).isFalse();
    }

    @Test
    void largeTableIsSampled() {
        assertThat(ProfileSql.source("FINSERV_DW", "INSURANCE", claims, 1000))
                .isEqualTo("\"FINSERV_DW\".\"INSURANCE\".\"CLAIMS\" SAMPLE (1000 ROWS)");
        assertThat(ProfileSql.sampled(claims, 1000)).isTrue();
    }

    @Test
    void viewIsLimited() {
        assertThat(ProfileSql.source("FINSERV_DW", "ANALYTICS", view, 500))
                .isEqualTo("(SELECT * FROM \"FINSERV_DW\".\"ANALYTICS\".\"CUSTOMER_360\" LIMIT 500)");
        assertThat(ProfileSql.sampled(view, 500)).isTrue();
    }

    @Test
    void oneAggregatePassForAllColumns() {
        String sql = ProfileSql.stats("SRC", customers.columns());
        assertThat(sql).startsWith("SELECT COUNT(*) AS N, ").endsWith(" FROM SRC")
                .contains("COUNT_IF(\"EMAIL\" IS NULL) AS C4_NULLS")
                .contains("APPROX_COUNT_DISTINCT(\"EMAIL\") AS C4_DISTINCT")
                .contains("MAX(LENGTH(\"EMAIL\")) AS C4_LEN")
                .contains("MIN(\"CREATED_AT\")::VARCHAR AS C9_MIN")
                .doesNotContain("LENGTH(\"CREATED_AT\")");
    }

    @Test
    void topValuesUnion() {
        String sql = ProfileSql.topValues("SRC", customers.columns(), List.of(7, 8));
        assertThat(sql).contains("UNION ALL").contains("SELECT 7 AS K, \"CUSTOMER_TIER\"::VARCHAR AS V")
                .contains("LIMIT 5");
    }

    @Test
    void samplesAreCapped() {
        assertThat(ProfileSql.samples("FINSERV_DW", "INSURANCE", claims)).endsWith("LIMIT 5");
    }
}
