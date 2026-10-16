package ai.jgp.hackathon.snowflake.product;

import java.util.Locale;

import ai.jgp.hackathon.snowflake.catalog.CatalogModel.ColumnInfo;

/** Snowflake column types to ODCS logical types, and a readable physical type. */
final class TypeMapper {

    private TypeMapper() {
    }

    static String logicalType(ColumnInfo column) {
        String type = column.dataType().toUpperCase(Locale.ROOT);
        if (type.startsWith("TIMESTAMP") || type.equals("DATETIME")) {
            return "timestamp";
        }
        return switch (type) {
            case "NUMBER", "DECIMAL", "NUMERIC" ->
                column.numericScale() == null || column.numericScale() == 0 ? "integer" : "number";
            case "INT", "INTEGER", "BIGINT", "SMALLINT", "TINYINT", "BYTEINT" -> "integer";
            case "FLOAT", "FLOAT4", "FLOAT8", "DOUBLE", "DOUBLE PRECISION", "REAL" -> "number";
            case "DATE" -> "date";
            case "TIME" -> "time";
            case "BOOLEAN" -> "boolean";
            case "VARIANT", "OBJECT" -> "object";
            case "ARRAY" -> "array";
            case "VECTOR" -> "vector";
            default -> "string";
        };
    }

    static String physicalType(ColumnInfo column) {
        String type = column.dataType().toUpperCase(Locale.ROOT);
        if (type.equals("NUMBER") && column.numericPrecision() != null) {
            return "NUMBER(" + column.numericPrecision() + ","
                    + (column.numericScale() == null ? 0 : column.numericScale()) + ")";
        }
        if (type.equals("TEXT") && column.maxLength() != null) {
            return "VARCHAR(" + column.maxLength() + ")";
        }
        return type;
    }
}
