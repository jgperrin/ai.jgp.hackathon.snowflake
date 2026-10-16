package ai.jgp.hackathon.snowflake.enrich;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import ai.jgp.hackathon.snowflake.enrich.EnrichService.EnrichResult;
import ai.jgp.hackathon.snowflake.enrich.ProposalApplier.ApplyRequest;
import ai.jgp.hackathon.snowflake.product.Bundle;

@RestController
class EnrichController {

    private final EnrichService enrich;
    private final EnrichJobs jobs;

    EnrichController(EnrichService enrich, EnrichJobs jobs) {
        this.enrich = enrich;
        this.jobs = jobs;
    }

    @PostMapping("/api/enrich")
    EnrichResult enrich(@RequestBody Bundle bundle) {
        return enrich.enrich(bundle);
    }

    @PostMapping("/api/enrich/jobs")
    ResponseEntity<EnrichJobs.JobView> start(@RequestBody Bundle bundle) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(jobs.start(bundle));
    }

    @GetMapping("/api/enrich/jobs/{id}")
    EnrichJobs.JobView job(@PathVariable String id) {
        return jobs.get(id);
    }

    @PostMapping("/api/products/apply")
    Bundle apply(@RequestBody ApplyRequest request) {
        return enrich.apply(request.bundle(), request.accepted() == null ? java.util.List.of() : request.accepted());
    }
}
