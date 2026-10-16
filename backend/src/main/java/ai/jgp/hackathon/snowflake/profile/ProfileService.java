package ai.jgp.hackathon.snowflake.profile;

import ai.jgp.hackathon.snowflake.profile.ProfileModel.ProfileRequest;
import ai.jgp.hackathon.snowflake.profile.ProfileModel.SchemaProfile;

public interface ProfileService {

    SchemaProfile profile(ProfileRequest request);
}
