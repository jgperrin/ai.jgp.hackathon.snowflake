package ai.jgp.hackathon.snowflake.catalog;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ai.jgp.hackathon.snowflake.catalog.CatalogModel.DatabaseInfo;
import ai.jgp.hackathon.snowflake.catalog.CatalogModel.SchemaInfo;
import ai.jgp.hackathon.snowflake.catalog.CatalogModel.TableInfo;

@RestController
@RequestMapping("/api/catalog")
class CatalogController {

    private final CatalogService catalog;

    CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/databases")
    List<DatabaseInfo> databases() {
        return catalog.databases();
    }

    @GetMapping("/databases/{database}/schemas")
    List<SchemaInfo> schemas(@PathVariable String database) {
        return catalog.schemas(Identifiers.require(database, "database"));
    }

    @GetMapping("/databases/{database}/schemas/{schema}/tables")
    List<TableInfo> tables(@PathVariable String database, @PathVariable String schema) {
        return catalog.tables(Identifiers.require(database, "database"), Identifiers.require(schema, "schema"));
    }
}
