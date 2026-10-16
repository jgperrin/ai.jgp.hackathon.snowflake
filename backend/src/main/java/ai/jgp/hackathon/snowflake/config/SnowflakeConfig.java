package ai.jgp.hackathon.snowflake.config;

import java.time.Duration;

import javax.sql.DataSource;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@Configuration
@Profile("!local")
@EnableConfigurationProperties(CortexProperties.class)
class SnowflakeConfig {

    /**
     * The driver is deliberately not set by class name: Snowflake JDBC 4.x moved its public
     * API to net.snowflake.client.api.*, so pinning the old class name breaks on upgrade.
     * Hikari resolves the driver from the jdbc:snowflake:// URL instead.
     */
    @Bean
    DataSource snowflakeDataSource(CortexProperties properties) {
        if (properties.account() == null || properties.account().isBlank()) {
            throw new IllegalStateException(
                    "Snowflake is not configured. Set SNOWFLAKE_ACCOUNT, SNOWFLAKE_USER and "
                            + "SNOWFLAKE_PASSWORD, or start with the 'local' profile for stubbed answers.");
        }
        HikariConfig config = new HikariConfig();
        config.setPoolName("snowflake");
        config.setJdbcUrl(properties.jdbcUrl());
        config.setUsername(properties.user());
        config.setPassword(properties.password());
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(1);
        // Keep both well under Snowflake's own idle timeout so the pool retires a connection
        // before the server does, rather than handing out one the server has already closed.
        config.setMaxLifetime(Duration.ofMinutes(50).toMillis());
        config.setKeepaliveTime(Duration.ofMinutes(5).toMillis());
        return new HikariDataSource(config);
    }
}
