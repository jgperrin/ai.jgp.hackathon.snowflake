package ai.jgp.hackathon.snowflake.catalog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import ai.jgp.hackathon.snowflake.catalog.CatalogModel.ColumnInfo;
import ai.jgp.hackathon.snowflake.catalog.CatalogModel.DatabaseInfo;
import ai.jgp.hackathon.snowflake.catalog.CatalogModel.ForeignKey;
import ai.jgp.hackathon.snowflake.catalog.CatalogModel.SchemaInfo;
import ai.jgp.hackathon.snowflake.catalog.CatalogModel.TableInfo;

@Service
@Profile("!local")
class JdbcCatalogService implements CatalogService {

    private static final Logger log = LoggerFactory.getLogger(JdbcCatalogService.class);

    private final JdbcTemplate jdbc;

    JdbcCatalogService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<DatabaseInfo> databases() {
        return jdbc.query("SHOW DATABASES",
                (rs, i) -> {
                    String name = rs.getString("name");
                    return new DatabaseInfo(name, blankToNull(rs.getString("comment")),
                            SystemDatabases.isSystem(name));
                });
    }

    @Override
    public List<SchemaInfo> schemas(String database) {
        String sql = "SELECT SCHEMA_NAME, COMMENT FROM " + Identifiers.quote(database, "database")
                + ".INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME <> 'INFORMATION_SCHEMA' ORDER BY 1";
        return jdbc.query(sql, (rs, i) -> new SchemaInfo(rs.getString(1), blankToNull(rs.getString(2))));
    }

    @Override
    public List<TableInfo> tables(String database, String schema) {
        String db = Identifiers.quote(database, "database");
        Identifiers.require(schema, "schema");

        Map<String, List<ColumnInfo>> columns = new HashMap<>();
        jdbc.query("SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COMMENT, ORDINAL_POSITION, "
                + "NUMERIC_PRECISION, NUMERIC_SCALE, CHARACTER_MAXIMUM_LENGTH FROM " + db
                + ".INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = ? ORDER BY TABLE_NAME, ORDINAL_POSITION",
                rs -> {
                    columns.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(new ColumnInfo(
                            rs.getString(2), rs.getString(3), "YES".equals(rs.getString(4)),
                            blankToNull(rs.getString(5)), rs.getInt(6), false, null,
                            intOrNull(rs.getObject(7)), intOrNull(rs.getObject(8)),
                            intOrNull(rs.getObject(9))));
                }, schema);

        Map<String, Map<String, Integer>> primaryKeys = primaryKeys(database, schema);
        Map<String, List<ForeignKey>> foreignKeys = foreignKeys(database, schema);

        Map<String, TableInfo> tables = new LinkedHashMap<>();
        jdbc.query("SELECT TABLE_NAME, TABLE_TYPE, ROW_COUNT, COMMENT FROM " + db
                + ".INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ? ORDER BY TABLE_NAME", rs -> {
                    String name = rs.getString(1);
                    Map<String, Integer> pk = primaryKeys.getOrDefault(name, Map.of());
                    List<ColumnInfo> cols = columns.getOrDefault(name, List.of()).stream()
                            .map(c -> pk.containsKey(c.name()) ? c.withPrimaryKey(pk.get(c.name())) : c)
                            .toList();
                    tables.put(name, new TableInfo(name,
                            rs.getString(2).contains("VIEW") ? "VIEW" : "TABLE",
                            rs.getObject(3) instanceof Number n ? n.longValue() : null, blankToNull(rs.getString(4)), cols,
                            foreignKeys.getOrDefault(name, List.of())));
                }, schema);
        return List.copyOf(tables.values());
    }

    /** Declared primary keys. Snowflake does not enforce them, and many schemas declare none. */
    private Map<String, Map<String, Integer>> primaryKeys(String database, String schema) {
        Map<String, Map<String, Integer>> result = new HashMap<>();
        try {
            jdbc.query("SHOW PRIMARY KEYS IN SCHEMA " + Identifiers.quote(database, "database") + "."
                    + Identifiers.quote(schema, "schema"), rs -> {
                        result.computeIfAbsent(rs.getString("table_name"), k -> new HashMap<>())
                                .put(rs.getString("column_name"), rs.getInt("key_sequence"));
                    });
        } catch (DataAccessException e) {
            log.warn("Could not read primary keys of {}.{}: {}", database, schema, e.getMessage());
        }
        return result;
    }

    private Map<String, List<ForeignKey>> foreignKeys(String database, String schema) {
        Map<String, List<ForeignKey>> result = new HashMap<>();
        try {
            jdbc.query("SHOW IMPORTED KEYS IN SCHEMA " + Identifiers.quote(database, "database") + "."
                    + Identifiers.quote(schema, "schema"), rs -> {
                        result.computeIfAbsent(rs.getString("fk_table_name"), k -> new ArrayList<>())
                                .add(new ForeignKey(rs.getString("fk_column_name"),
                                        rs.getString("pk_schema_name"), rs.getString("pk_table_name"),
                                        rs.getString("pk_column_name")));
                    });
        } catch (DataAccessException e) {
            log.warn("Could not read foreign keys of {}.{}: {}", database, schema, e.getMessage());
        }
        return result;
    }

    private static Integer intOrNull(Object o) {
        return o instanceof Number n ? n.intValue() : null;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
