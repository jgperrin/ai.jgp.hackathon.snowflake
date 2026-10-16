package ai.jgp.hackathon.snowflake.quality;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import ai.jgp.hackathon.snowflake.product.Bundle;
import ai.jgp.hackathon.snowflake.quality.QualityModel.RunResult;

@RestController
class QualityController {

    private final QualityService quality;

    QualityController(QualityService quality) {
        this.quality = quality;
    }

    @PostMapping("/api/quality/run")
    RunResult run(@RequestBody Bundle bundle) {
        return quality.run(bundle);
    }
}
