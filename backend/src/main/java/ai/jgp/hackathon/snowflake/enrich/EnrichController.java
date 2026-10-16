package ai.jgp.hackathon.snowflake.enrich;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import ai.jgp.hackathon.snowflake.enrich.EnrichService.EnrichResult;
import ai.jgp.hackathon.snowflake.enrich.ProposalApplier.ApplyRequest;
import ai.jgp.hackathon.snowflake.product.Bundle;

@RestController
class EnrichController {

    private final EnrichService enrich;

    EnrichController(EnrichService enrich) {
        this.enrich = enrich;
    }

    @PostMapping("/api/enrich")
    EnrichResult enrich(@RequestBody Bundle bundle) {
        return enrich.enrich(bundle);
    }

    @PostMapping("/api/products/apply")
    Bundle apply(@RequestBody ApplyRequest request) {
        return enrich.apply(request.bundle(), request.accepted() == null ? java.util.List.of() : request.accepted());
    }
}
