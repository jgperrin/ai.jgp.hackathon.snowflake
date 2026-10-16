package ai.jgp.hackathon.snowflake.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Basic sign-in for the hackathon: one demo account whose credentials are shown on the sign-in
 * page so judges can try the app. Replaced by Bitol OAuth sign-in later.
 *
 * @param required false only in tests that exercise other endpoints; every request then runs
 *                 as the demo user
 */
@ConfigurationProperties("contextbuilder.auth")
public record AuthProperties(
        @DefaultValue("jgp") String demoUser,
        @DefaultValue("odcs") String demoPassword,
        @DefaultValue("true") boolean required) {
}
