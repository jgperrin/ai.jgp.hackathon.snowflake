package ai.jgp.hackathon.snowflake.product;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

import ai.jgp.hackathon.snowflake.product.Bundle.Problem;
import ai.jgp.hackathon.snowflake.product.Bundle.Validation;
import tools.jackson.databind.json.JsonMapper;

/** Validates against the official JSON schemas, bundled under {@code standards/}. */
@Component
public class StandardsValidator {

    private final JsonMapper json = JsonMapper.builder().build();
    private final Schema odcs;
    private final Schema odps;
    private final Schema ossie;

    public StandardsValidator() {
        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2019_09);
        this.odcs = load(registry, "standards/odcs-json-schema-v3.2.0.json");
        this.odps = load(registry, "standards/odps-json-schema-v1.1.0.json");
        this.ossie = load(SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12),
                "standards/ossie-schema-0.2.0.dev0.json");
    }

    static Schema load(SchemaRegistry registry, String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return registry.getSchema(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load " + path, e);
        }
    }

    public Validation validate(Map<String, Object> product, List<Map<String, Object>> contracts) {
        List<Problem> problems = new ArrayList<>(check(odps, product, "ODPS " + product.get("id")));
        for (Map<String, Object> contract : contracts) {
            problems.addAll(check(odcs, contract, "ODCS " + contract.get("id")));
        }
        return new Validation(problems.isEmpty(), problems);
    }

    List<Problem> check(Schema schema, Map<String, Object> document, String label) {
        return schema.validate(json.valueToTree(document)).stream()
                .map(e -> new Problem(label, String.valueOf(e.getInstanceLocation()), e.getMessage()))
                .toList();
    }

    /** OSI/Apache Ossie semantic model; returns readable errors, empty when valid. */
    public List<String> validateOsi(Map<String, Object> model) {
        return check(ossie, model, "OSI").stream().map(p -> p.path() + ": " + p.message()).toList();
    }

    public Validation validateBundle(Bundle bundle) {
        return validate(bundle.product(), bundle.contracts());
    }
}
