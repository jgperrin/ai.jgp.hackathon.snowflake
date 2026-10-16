package ai.jgp.hackathon.snowflake.cortex;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/cortex")
class CortexController {

    private final CortexClient cortexClient;

    CortexController(CortexClient cortexClient) {
        this.cortexClient = cortexClient;
    }

    @PostMapping("/complete")
    CortexResponse complete(@Valid @RequestBody CortexRequest request) {
        return new CortexResponse(cortexClient.complete(request.prompt()));
    }
}
