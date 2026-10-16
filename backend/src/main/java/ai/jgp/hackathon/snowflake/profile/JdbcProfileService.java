package ai.jgp.hackathon.snowflake.profile;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import ai.jgp.hackathon.snowflake.catalog.CatalogModel.ColumnInfo;
import ai.jgp.hackathon.snowflake.catalog.CatalogModel.TableInfo;
import ai.jgp.hackathon.snowflake.catalog.CatalogService;
import ai.jgp.hackathon.snowflake.profile.ProfileModel.ColumnProfile;
import ai.jgp.hackathon.snowflake.profile.ProfileModel.ProfileRequest;
import ai.jgp.hackathon.snowflake.profile.ProfileModel.SchemaProfile;
import ai.jgp.hackathon.snowflake.profile.ProfileModel.TableProfile;
import ai.jgp.hackathon.snowflake.profile.ProfileModel.ValueCount;

@Service
@Profile("!local")
class JdbcProfileService implements ProfileService {

    private final JdbcTemplate jdbc;
    private final CatalogService catalog;
    private final long cap;

    JdbcProfileService(JdbcTemplate jdbc, CatalogService catalog,
            @Value("${profile.sample-rows:100000}") long cap) {
        this.jdbc = jdbc;
        this.catalog = catalog;
        this.cap = cap;
    }

    @Override
    public SchemaProfile profile(ProfileRequest request) {
        List<TableProfile> out = new ArrayList<>();
        for (TableInfo table : Profiles.select(catalog.tables(request.database(), request.schema()),
                request.tables())) {
            out.add(profile(request.database(), request.schema(), table));
        }
        return new SchemaProfile(request.database(), request.schema(), out);
    }

    private TableProfile profile(String database, String schema, TableInfo table) {
        List<ColumnInfo> cols = table.columns();
        String source = ProfileSql.source(database, schema, table, cap);
        Map<String, Object> stats = jdbc.queryForMap(ProfileSql.stats(source, cols));
        long n = number(stats.get("N"));

        List<Integer> lowCardinality = new ArrayList<>();
        for (int i = 0; i < cols.size(); i++) {
            long distinct = number(stats.get("C" + i + "_DISTINCT"));
            if (distinct > 0 && distinct <= Profiles.TOP_VALUES_MAX_DISTINCT) {
                lowCardinality.add(i);
            }
        }
        Map<Integer, List<ValueCount>> top = new HashMap<>();
        if (!lowCardinality.isEmpty()) {
            jdbc.query(ProfileSql.topValues(source, cols, lowCardinality), rs -> {
                top.computeIfAbsent(rs.getInt("K"), k -> new ArrayList<>())
                        .add(new ValueCount(rs.getString("V"), rs.getLong("N")));
            });
        }
        Map<String, List<String>> samples = new HashMap<>();
        jdbc.query(ProfileSql.samples(database, schema, table), (ResultSet rs) -> {
            for (ColumnInfo c : cols) {
                samples.computeIfAbsent(c.name(), k -> new ArrayList<>()).add(string(rs, c.name()));
            }
        });

        List<ColumnProfile> columns = new ArrayList<>();
        for (int i = 0; i < cols.size(); i++) {
            ColumnInfo c = cols.get(i);
            long nulls = number(stats.get("C" + i + "_NULLS"));
            Object len = stats.get("C" + i + "_LEN");
            columns.add(new ColumnProfile(c.name(), c.dataType(), nulls, n == 0 ? 0 : (double) nulls / n,
                    number(stats.get("C" + i + "_DISTINCT")), (String) stats.get("C" + i + "_MIN"),
                    (String) stats.get("C" + i + "_MAX"), len == null ? null : (int) number(len),
                    top.getOrDefault(i, List.of()), samples.getOrDefault(c.name(), List.of())));
        }
        boolean sampled = ProfileSql.sampled(table, cap);
        return new TableProfile(table.name(), table.rowCount() != null ? table.rowCount() : null, sampled, n,
                columns);
    }

    private static String string(ResultSet rs, String column) throws SQLException {
        Object v = rs.getObject(column);
        return v == null ? null : v.toString();
    }

    private static long number(Object o) {
        return o instanceof Number n ? n.longValue() : 0;
    }
}
