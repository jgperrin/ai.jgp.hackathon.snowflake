package ai.jgp.hackathon.snowflake.profile;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ai.jgp.hackathon.snowflake.catalog.Identifiers;
import ai.jgp.hackathon.snowflake.profile.ProfileModel.ProfileRequest;
import ai.jgp.hackathon.snowflake.profile.ProfileModel.SchemaProfile;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/profile")
class ProfileController {

    private final ProfileService profiles;

    ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @PostMapping
    SchemaProfile profile(@Valid @RequestBody ProfileRequest request) {
        Identifiers.require(request.database(), "database");
        Identifiers.require(request.schema(), "schema");
        if (request.tables() != null) {
            request.tables().forEach(t -> Identifiers.require(t, "table"));
        }
        return profiles.profile(request);
    }
}
