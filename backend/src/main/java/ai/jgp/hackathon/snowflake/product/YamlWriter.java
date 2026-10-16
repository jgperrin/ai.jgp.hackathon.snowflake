package ai.jgp.hackathon.snowflake.product;

import org.springframework.stereotype.Component;

import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;

@Component
public class YamlWriter {

    private final YAMLMapper yaml = YAMLMapper.builder()
            .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
            .enable(YAMLWriteFeature.MINIMIZE_QUOTES)
            .build();

    public String write(Object document) {
        return yaml.writeValueAsString(document);
    }
}
