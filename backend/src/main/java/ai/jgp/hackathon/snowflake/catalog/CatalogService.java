package ai.jgp.hackathon.snowflake.catalog;

import java.util.List;

import ai.jgp.hackathon.snowflake.catalog.CatalogModel.DatabaseInfo;
import ai.jgp.hackathon.snowflake.catalog.CatalogModel.SchemaInfo;
import ai.jgp.hackathon.snowflake.catalog.CatalogModel.TableInfo;

/** What the connected Snowflake role can see. Metadata only: never scans data. */
public interface CatalogService {

    List<DatabaseInfo> databases();

    List<SchemaInfo> schemas(String database);

    List<TableInfo> tables(String database, String schema);
}
