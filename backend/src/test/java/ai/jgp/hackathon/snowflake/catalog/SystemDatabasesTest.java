package ai.jgp.hackathon.snowflake.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SystemDatabasesTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "SNOWFLAKE",
            "SNOWFLAKE_SAMPLE_DATA",
            "ORGDATACLOUD$INTERNAL$OS_BUILDING_SAMPLE_DATA",
            "ORGDATACLOUD$INTERNAL$TRANSFORM_HEALTHCARE_PERSISTED_TABLES",
            "USERS$JGP"})
    void recognisesEveryPattern(String name) {
        assertThat(SystemDatabases.isSystem(name)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"snowflake", "Snowflake_Sample_Data", "orgdatacloud$internal$x", "users$jgp"})
    void isCaseInsensitive(String name) {
        assertThat(SystemDatabases.isSystem(name)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "FINSERV_DW", "USERS_ARCHIVE", "MY_SNOWFLAKE", "SNOWFLAKE_DW", "DEFAULT_DATABASE",
            "AUTO_MFG_DW", "ORGDATACLOUD", "USERS"})
    void ordinaryNamesAreNotSystem(String name) {
        assertThat(SystemDatabases.isSystem(name)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void nullOrEmptyIsNotSystem(String name) {
        assertThat(SystemDatabases.isSystem(name)).isFalse();
    }
}
