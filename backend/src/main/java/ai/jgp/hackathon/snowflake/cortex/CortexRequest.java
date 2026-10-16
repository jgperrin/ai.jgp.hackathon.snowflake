package ai.jgp.hackathon.snowflake.cortex;

import jakarta.validation.constraints.NotBlank;

public record CortexRequest(@NotBlank String prompt) {
}
