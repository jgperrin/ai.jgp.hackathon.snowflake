package ai.jgp.hackathon.snowflake.profile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import ai.jgp.hackathon.snowflake.catalog.CatalogModel.ColumnInfo;
import ai.jgp.hackathon.snowflake.catalog.CatalogModel.TableInfo;
import ai.jgp.hackathon.snowflake.catalog.CatalogService;
import ai.jgp.hackathon.snowflake.profile.ProfileModel.ColumnProfile;
import ai.jgp.hackathon.snowflake.profile.ProfileModel.ProfileRequest;
import ai.jgp.hackathon.snowflake.profile.ProfileModel.SchemaProfile;
import ai.jgp.hackathon.snowflake.profile.ProfileModel.TableProfile;
import ai.jgp.hackathon.snowflake.profile.ProfileModel.ValueCount;

/** Plausible, clearly fake statistics for the offline demo. */
@Service
@Profile("local")
class StubProfileService implements ProfileService {

    private final CatalogService catalog;

    StubProfileService(CatalogService catalog) {
        this.catalog = catalog;
    }

    @Override
    public SchemaProfile profile(ProfileRequest request) {
        List<TableProfile> tables = new ArrayList<>();
        for (TableInfo t : Profiles.select(catalog.tables(request.database(), request.schema()),
                request.tables())) {
            long n = t.rowCount() == null ? 1000 : t.rowCount();
            List<ColumnProfile> cols = new ArrayList<>();
            for (ColumnInfo c : t.columns()) {
                long nulls = c.nullable() ? n / 50 : 0;
                boolean category = c.name().endsWith("_TYPE") || c.name().endsWith("_TIER")
                        || c.name().endsWith("STATUS") || c.name().endsWith("_LEVEL") || c.name().equals("SEVERITY");
                List<ValueCount> top = category
                        ? List.of(new ValueCount("A", n / 2), new ValueCount("B", n / 3), new ValueCount("C", n / 6))
                        : List.of();
                cols.add(new ColumnProfile(c.name(), c.dataType(), nulls, (double) nulls / n,
                        c.primaryKey() ? n : category ? 3 : n / 3, null, null, null, top,
                        List.of(c.name().toLowerCase(Locale.ROOT) + "_1", c.name().toLowerCase(Locale.ROOT) + "_2")));
            }
            tables.add(new TableProfile(t.name(), t.rowCount(), false, n, cols));
        }
        return new SchemaProfile(request.database(), request.schema(), tables);
    }
}
