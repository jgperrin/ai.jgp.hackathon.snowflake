package ai.jgp.hackathon.snowflake.catalog;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import ai.jgp.hackathon.snowflake.catalog.CatalogModel.DatabaseInfo;
import ai.jgp.hackathon.snowflake.catalog.CatalogModel.SchemaInfo;
import ai.jgp.hackathon.snowflake.catalog.CatalogModel.TableInfo;
import ai.jgp.hackathon.snowflake.stub.Finserv;

@Service
@Profile("local")
class StubCatalogService implements CatalogService {

    @Override
    public List<DatabaseInfo> databases() {
        return List.of(new DatabaseInfo(Finserv.DATABASE, "Stub: financial services & insurance",
                SystemDatabases.isSystem(Finserv.DATABASE)));
    }

    @Override
    public List<SchemaInfo> schemas(String database) {
        requireKnown(database);
        return Finserv.SCHEMAS.keySet().stream().map(s -> new SchemaInfo(s, null)).toList();
    }

    @Override
    public List<TableInfo> tables(String database, String schema) {
        requireKnown(database);
        List<TableInfo> tables = Finserv.SCHEMAS.get(schema);
        if (tables == null) {
            throw new IllegalArgumentException("Unknown schema: " + schema);
        }
        return tables;
    }

    private static void requireKnown(String database) {
        if (!Finserv.DATABASE.equals(database)) {
            throw new IllegalArgumentException("Unknown database: " + database);
        }
    }
}
