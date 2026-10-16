package ai.jgp.hackathon.snowflake.connection;

import javax.sql.DataSource;

import ai.jgp.hackathon.snowflake.connection.ConnectionModel.Profile;

/** Opens and checks a connection pool for one profile; the seam that keeps tests off Snowflake. */
public interface SnowflakeConnector {

    /** Opens a pool and proves it works, or throws with Snowflake's reason. */
    DataSource open(Profile profile);

    void close(DataSource dataSource);
}
