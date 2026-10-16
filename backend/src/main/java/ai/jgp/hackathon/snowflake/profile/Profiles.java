package ai.jgp.hackathon.snowflake.profile;

import java.util.List;
import java.util.Set;

import ai.jgp.hackathon.snowflake.catalog.CatalogModel.TableInfo;

final class Profiles {

    /** Columns with at most this many distinct values get their top values listed. */
    static final int TOP_VALUES_MAX_DISTINCT = 25;

    private Profiles() {
    }

    static List<TableInfo> select(List<TableInfo> available, List<String> wanted) {
        if (wanted == null || wanted.isEmpty()) {
            return available;
        }
        Set<String> names = Set.copyOf(wanted);
        List<TableInfo> picked = available.stream().filter(t -> names.contains(t.name())).toList();
        if (picked.size() != names.size()) {
            throw new IllegalArgumentException("Unknown table(s): " + wanted);
        }
        return picked;
    }
}
