package com.springlab.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PortReferencesTest {

    @Test
    void realPortReferencesAreDetected() {
        for (String leak : new String[]{"localhost:3306", "jdbc:mysql://db:3306/springlab", "port=3306", "Port: 3306", "\"port\":3306", "host=db;3306/springlab", "x:3306"}) {
            assertThat(PortReferences.mentions(leak)).as(leak).isTrue();
        }
    }

    @Test
    void theSameDigitsInsideTimestampsAndNumbersAreNotAReference() {
        for (String harmless : new String[]{"\"createdat\":\"2026-11-03t23:17:37.873306z\"", "2026-11-03T23:17:37.133306Z", "\"durationmillis\":3306", "id 13306 and 33067",
                "\"seq\":3306,", "uuid 0a3306ff", "a 3306 b"}) {
            assertThat(PortReferences.mentions(harmless)).as(harmless).isFalse();
        }
    }
}
