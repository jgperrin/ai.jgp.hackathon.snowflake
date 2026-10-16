package ai.jgp.hackathon.snowflake.osi;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import ai.jgp.hackathon.snowflake.product.Bundle;
import ai.jgp.hackathon.snowflake.product.StandardsValidator;
import ai.jgp.hackathon.snowflake.product.YamlWriter;

@RestController
class OsiController {

    private final StandardsValidator validator;
    private final YamlWriter yaml;

    OsiController(StandardsValidator validator, YamlWriter yaml) {
        this.validator = validator;
        this.yaml = yaml;
    }

    public record OsiExport(String yaml, boolean valid, List<String> errors) {
    }

    @PostMapping("/api/export/osi")
    OsiExport export(@RequestBody Bundle bundle) {
        Map<String, Object> model = OsiExporter.export(bundle.product(), bundle.contracts());
        List<String> errors = validator.validateOsi(model);
        return new OsiExport(yaml.write(model), errors.isEmpty(), errors);
    }
}
