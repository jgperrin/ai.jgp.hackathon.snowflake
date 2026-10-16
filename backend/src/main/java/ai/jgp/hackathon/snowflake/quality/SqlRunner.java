package ai.jgp.hackathon.snowflake.quality;

/** Runs one statement that returns a single number. The seam between rule logic and Snowflake. */
public interface SqlRunner {

    Double scalar(String sql);
}
