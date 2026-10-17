package ai.jgp.hackathon.snowflake.product;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

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

    /** What the documents are called, so a name says what the artifact is and not just its source. */
    static final String PRODUCT_SUFFIX = " data product";
    static final String CONTRACT_SUFFIX = " data contract";

    /**
     * Namespace for this app's name-based ids. A fixed UUID of our own, written once and never
     * touched: changing it would change every id this app has ever produced.
     */
    private static final UUID ID_NAMESPACE = UUID.fromString("3f7c1e6a-9b2d-4c58-9a71-2e4d8b5f0c31");

    /** Where the data lives; written into each contract's {@code servers} block. */
    public record Server(String account, String warehouse) {
    }

    private ContractGenerator() {
    }

    /** Stable ids may not contain '.', '#', '/', '\', '@', '!', '%', '&', '^' or whitespace (ODCS RFC-0026a). */
    public static String id(String... parts) {
        return String.join("-", parts).toLowerCase(Locale.ROOT).replaceAll("[\\s.#/\\\\@!%&^]+", "_");
    }

    public static String productName(String database) {
        return database + PRODUCT_SUFFIX;
    }

    public static String contractName(String database, String schema) {
        return database + "." + schema + CONTRACT_SUFFIX;
    }

    /**
     * A UUID v5 (name-based over SHA-1, RFC 4122) of the entity's own name, in this app's namespace.
     *
     * Deterministic on purpose: the same database or schema always yields the same id, so a product
     * keeps its identity across regenerations, and two different names cannot collide the way
     * slugified ones can — the old id dropped every character a Snowflake identifier may not use.
     */
    public static String artifactId(String name) {
        byte[] hash = sha1(namespaceBytes(), name.getBytes(StandardCharsets.UTF_8));
        hash[6] = (byte) ((hash[6] & 0x0f) | 0x50);
        hash[8] = (byte) ((hash[8] & 0x3f) | 0x80);
        return hex(hash, 0, 4) + '-' + hex(hash, 4, 2) + '-' + hex(hash, 6, 2) + '-' + hex(hash, 8, 2) + '-'
                + hex(hash, 10, 6);
    }

    private static byte[] namespaceBytes() {
        return ByteBuffer.allocate(16).putLong(ID_NAMESPACE.getMostSignificantBits())
                .putLong(ID_NAMESPACE.getLeastSignificantBits()).array();
    }

    private static byte[] sha1(byte[] namespace, byte[] name) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.update(namespace);
            return digest.digest(name);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is required by RFC 4122 and unavailable", e);
        }
    }

    private static String hex(byte[] bytes, int offset, int length) {
        StringBuilder out = new StringBuilder(length * 2);
        for (int i = offset; i < offset + length; i++) {
            out.append(Character.forDigit((bytes[i] >> 4) & 0xf, 16)).append(Character.forDigit(bytes[i] & 0xf, 16));
        }
        return out.toString();
    }

    public static Map<String, Object> contract(String database, String schema, List<TableInfo> tables,
            Server server, Instant now) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("apiVersion", ODCS_VERSION);
        c.put("kind", "DataContract");
        c.put("id", artifactId(contractName(database, schema)));
        c.put("name", contractName(database, schema));
        c.put("version", INITIAL_VERSION);
        c.put("status", "draft");
        // Deprecated in ODCS v3.1.0, and the real link is the ODPS output port's contractId; kept in
        // step with the product's name rather than the raw database name.
        c.put("dataProduct", productName(database));
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
        p.put("id", artifactId(productName(database)));
        p.put("name", productName(database));
        p.put("version", INITIAL_VERSION);
        p.put("status", "draft");
        p.put("description", Map.of("purpose", "Data product built from the " + database
                + " Snowflake database. Each output port is backed by an ODCS data contract."));
        List<Map<String, Object>> ports = new ArrayList<>();
        for (Map<String, Object> contract : contracts) {
            String schema = schemaOf(contract);
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

    /**
     * The schema a contract covers, read from its server block. It used to be sliced out of the
     * contract's name, which stopped working the moment the name became more than the source.
     */
    private static String schemaOf(Map<String, Object> contract) {
        Object servers = contract.get("servers");
        if (servers instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> server
                && server.get("schema") != null) {
            return String.valueOf(server.get("schema"));
        }
        throw new IllegalArgumentException("Contract " + contract.get("id") + " has no Snowflake schema");
    }
}
