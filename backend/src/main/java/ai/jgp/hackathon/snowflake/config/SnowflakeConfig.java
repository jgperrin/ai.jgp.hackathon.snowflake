package ai.jgp.hackathon.snowflake.config;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import ai.jgp.hackathon.snowflake.connection.ConnectionRegistry;
import ai.jgp.hackathon.snowflake.connection.UserRoutingDataSource;

/**
 * No global Snowflake connection: each signed-in user has their own pool, opened from their
 * profile. The app therefore boots with no Snowflake settings at all.
 */
@Configuration
@Profile("!local")
class SnowflakeConfig {

    @Bean
    DataSource snowflakeDataSource(ConnectionRegistry registry) {
        return new UserRoutingDataSource(registry);
    }
}
