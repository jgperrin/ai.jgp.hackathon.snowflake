package ai.jgp.hackathon.snowflake.product;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/** The user's selection: one database (the product), some schemas (the output ports), some tables. */
public record GenerateRequest(@NotBlank String database, @NotEmpty @Valid List<SchemaSelection> schemas) {

    /** An empty {@code tables} list means every table and view in the schema. */
    public record SchemaSelection(@NotBlank String schema, List<String> tables) {
    }
}
