package ai.jgp.hackathon.snowflake.catalog;

import java.util.List;

/** JSON shapes served by the catalog API. */
public final class CatalogModel {

    private CatalogModel() {
    }

    /** {@code system} is true for Snowflake's own databases; see {@link SystemDatabases}. */
    public record DatabaseInfo(String name, String comment, boolean system) {
    }

    public record SchemaInfo(String name, String comment) {
    }

    public record ColumnInfo(
            String name,
            String dataType,
            boolean nullable,
            String comment,
            int ordinal,
            boolean primaryKey,
            Integer primaryKeyPosition,
            Integer numericPrecision,
            Integer numericScale,
            Integer maxLength) {

        public ColumnInfo withPrimaryKey(int position) {
            return new ColumnInfo(name, dataType, nullable, comment, ordinal, true, position,
                    numericPrecision, numericScale, maxLength);
        }
    }

    public record ForeignKey(String column, String refSchema, String refTable, String refColumn) {
    }

    public record TableInfo(
            String name,
            String kind,
            Long rowCount,
            String comment,
            List<ColumnInfo> columns,
            List<ForeignKey> foreignKeys) {
    }
}
