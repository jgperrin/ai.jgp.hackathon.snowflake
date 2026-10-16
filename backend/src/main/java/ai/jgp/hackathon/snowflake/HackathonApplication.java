package ai.jgp.hackathon.snowflake;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

/**
 * DataSourceAutoConfiguration is switched off because the Snowflake DataSource is built
 * explicitly in SnowflakeConfig. Leaving it on would make the app fail to start under the
 * 'local' profile, where no Snowflake connection is configured or wanted.
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class HackathonApplication {

    public static void main(String[] args) {
        SpringApplication.run(HackathonApplication.class, args);
    }
}
