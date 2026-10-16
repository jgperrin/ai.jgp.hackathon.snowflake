package ai.jgp.hackathon.snowflake.product;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import ai.jgp.hackathon.snowflake.catalog.CatalogModel.TableInfo;
import ai.jgp.hackathon.snowflake.catalog.CatalogService;
import ai.jgp.hackathon.snowflake.catalog.Identifiers;
import ai.jgp.hackathon.snowflake.config.CortexProperties;
import ai.jgp.hackathon.snowflake.product.GenerateRequest.SchemaSelection;

@Service
public class ProductService {

    private final CatalogService catalog;
    private final StandardsValidator validator;
    private final YamlWriter yaml;
    private final ContractGenerator.Server server;
    private final Clock clock = Clock.systemUTC();

    ProductService(CatalogService catalog, StandardsValidator validator, YamlWriter yaml,
            CortexProperties properties) {
        this.catalog = catalog;
        this.validator = validator;
        this.yaml = yaml;
        String account = properties.account() == null || properties.account().isBlank()
                ? "local-stub" : properties.account();
        this.server = new ContractGenerator.Server(account, properties.warehouse());
    }

    public Bundle generate(GenerateRequest request) {
        String database = Identifiers.require(request.database(), "database");
        Instant now = clock.instant();
        List<Map<String, Object>> contracts = new ArrayList<>();
        for (SchemaSelection selection : request.schemas()) {
            String schema = Identifiers.require(selection.schema(), "schema");
            List<TableInfo> tables = select(catalog.tables(database, schema), selection.tables());
            if (tables.isEmpty()) {
                throw new IllegalArgumentException("No tables selected in " + database + "." + schema);
            }
            contracts.add(ContractGenerator.contract(database, schema, tables, server, now));
        }
        Map<String, Object> product = ContractGenerator.product(database, contracts, now);
        return new Bundle(product, contracts, validator.validate(product, contracts));
    }

    static List<TableInfo> select(List<TableInfo> available, List<String> wanted) {
        if (wanted == null || wanted.isEmpty()) {
            return available;
        }
        Set<String> names = Set.copyOf(wanted);
        List<TableInfo> picked = available.stream().filter(t -> names.contains(t.name())).toList();
        if (picked.size() != names.size()) {
            throw new IllegalArgumentException("Unknown table(s) in selection: " + wanted);
        }
        return picked;
    }

    /** YAML for the product and for each contract, keyed by contract id. */
    public Map<String, Object> yaml(Bundle bundle) {
        Map<String, String> contracts = new LinkedHashMap<>();
        for (Map<String, Object> contract : bundle.contracts()) {
            contracts.put(String.valueOf(contract.get("id")), yaml.write(contract));
        }
        return Map.of("product", yaml.write(bundle.product()), "contracts", contracts);
    }
}
