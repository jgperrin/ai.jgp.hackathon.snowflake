package ai.jgp.hackathon.snowflake.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IdentifiersTest {

    @Test
    void quotesSafeNames() {
        assertThat(Identifiers.qualified("FINSERV_DW", "CORE", "CUSTOMERS"))
                .isEqualTo("\"FINSERV_DW\".\"CORE\".\"CUSTOMERS\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1ABC", "A B", "A\"B", "A;DROP", "A.B", "A-B"})
    void refusesUnsafeNames(String name) {
        assertThatThrownBy(() -> Identifiers.quote(name, "table"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesNull() {
        assertThatThrownBy(() -> Identifiers.require(null, "schema"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
