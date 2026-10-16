package ai.jgp.hackathon.snowflake.product;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ai.jgp.hackathon.snowflake.catalog.CatalogModel.ColumnInfo;
import ai.jgp.hackathon.snowflake.catalog.CatalogModel.TableInfo;

/**
 * Builds the documents. Pure: no Snowflake access, so every mapping decision is unit-testable.
 * Database → ODPS v1.1.0 product; schema → ODCS v3.2.0 contract → output port; table → schema object.
 */
public final class ContractGenerator {

    public static final String ODCS_VERSION = "v3.2.0";
    public static final String ODPS_VERSION = "v1.1.0";
    static final String INITIAL_VERSION = "1.0.0";

    /** Where the data lives; written into each contract's {@code servers} block. */
    public record Server(String account, String warehouse) {
    }

    private ContractGenerator() {
    }

    /** Stable ids may not contain '.', '#', '/', '\', '@', '!', '%', '&', '^' or whitespace (ODCS RFC-0026a). */
    public static String id(String... parts) {
        return String.join("-", parts).toLowerCase(Locale.ROOT).replaceAll("[\\s.#/\\\\@!%&^]+", "_");
    }

    public static Map<String, Object> contract(String database, String schema, List<TableInfo> tables,
            Server server, Instant now) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("apiVersion", ODCS_VERSION);
        c.put("kind", "DataContract");
        c.put("id", id(database, schema));
        c.put("name", database + "." + schema);
        c.put("version", INITIAL_VERSION);
        c.put("status", "draft");
        c.put("dataProduct", database);
        c.put("description", Map.of("purpose", "Data contract for the " + schema + " schema of the "
                + database + " Snowflake database, generated from its catalog and profile."));
        Map<String, Object> srv = new LinkedHashMap<>();
        srv.put("id", "snowflake");
        srv.put("server", "snowflake");
        srv.put("type", "snowflake");
        srv.put("account", server.account());
        srv.put("database", database);
        srv.put("schema", schema);
        if (server.warehouse() != null && !server.warehouse().isBlank()) {
            srv.put("warehouse", server.warehouse());
        }
        c.put("servers", List.of(srv));
        c.put("schema", new ArrayList<>(tables.stream().map(ContractGenerator::schemaObject).toList()));
        c.put("contractCreatedTs", now.truncatedTo(ChronoUnit.SECONDS).toString());
        return c;
    }

    static Map<String, Object> schemaObject(TableInfo table) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("id", id(table.name()));
        o.put("name", table.name());
        o.put("physicalName", table.name());
        o.put("physicalType", "VIEW".equals(table.kind()) ? "view" : "table");
        o.put("logicalType", "object");
        if (table.comment() != null) {
            o.put("description", table.comment());
        }
        o.put("properties", new ArrayList<>(table.columns().stream().map(ContractGenerator::property).toList()));
        return o;
    }

    static Map<String, Object> property(ColumnInfo column) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", id(column.name()));
        p.put("name", column.name());
        p.put("physicalName", column.name());
        p.put("logicalType", TypeMapper.logicalType(column));
        p.put("physicalType", TypeMapper.physicalType(column));
        p.put("required", !column.nullable());
        if (column.primaryKey()) {
            p.put("primaryKey", true);
            p.put("primaryKeyPosition", column.primaryKeyPosition() == null ? 1 : column.primaryKeyPosition());
        }
        if (column.comment() != null) {
            p.put("description", column.comment());
        }
        return p;
    }

    /** The product: one output port per contract, each pointing at its contract id. */
    public static Map<String, Object> product(String database, List<Map<String, Object>> contracts, Instant now) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("apiVersion", ODPS_VERSION);
        p.put("kind", "DataProduct");
        p.put("id", id(database));
        p.put("name", database);
        p.put("version", INITIAL_VERSION);
        p.put("status", "draft");
        p.put("description", Map.of("purpose", "Data product built from the " + database
                + " Snowflake database. Each output port is backed by an ODCS data contract."));
        List<Map<String, Object>> ports = new ArrayList<>();
        for (Map<String, Object> contract : contracts) {
            String schema = String.valueOf(contract.get("name")).substring(database.length() + 1);
            Map<String, Object> port = new LinkedHashMap<>();
            port.put("id", id(schema));
            port.put("name", schema.toLowerCase(Locale.ROOT));
            port.put("version", String.valueOf(contract.get("version")));
            port.put("type", "tables");
            port.put("description", "Snowflake schema " + database + "." + schema);
            port.put("contractId", String.valueOf(contract.get("id")));
            ports.add(port);
        }
        p.put("outputPorts", ports);
        p.put("productCreatedTs", now.truncatedTo(ChronoUnit.SECONDS).toString());
        return p;
    }
}
