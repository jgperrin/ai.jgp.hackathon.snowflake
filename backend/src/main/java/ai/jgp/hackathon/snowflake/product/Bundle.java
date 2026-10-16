package ai.jgp.hackathon.snowflake.product;

import java.util.List;
import java.util.Map;

/**
 * One data product and the contracts behind its output ports, carried between the browser and
 * the backend as plain JSON. The documents are kept as maps so that every field the standards
 * allow round-trips, including ones this app never sets.
 */
public record Bundle(Map<String, Object> product, List<Map<String, Object>> contracts, Validation validation) {

    public record Validation(boolean valid, List<Problem> errors) {
    }

    public record Problem(String document, String path, String message) {
    }
}
