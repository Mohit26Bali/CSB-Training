package com.hackathon.food.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class RequiredSettingsTest {

    @Test
    void everythingMissingIsReportedByTheNameOfTheVariableToSet() {
        MockEnvironment env = new MockEnvironment();
        assertEquals(List.of("DB_PASSWORD", "DB_USERNAME", "JWT_SECRET"), RequiredSettings.missing(env));
    }

    @Test
    void anUnresolvedPlaceholderCountsAsMissing() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.datasource.username", "${DB_USERNAME}")
                .withProperty("spring.datasource.password", "${DB_PASSWORD}")
                .withProperty("app.jwt.secret", "${JWT_SECRET}");
        assertEquals(3, RequiredSettings.missing(env).size());
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> RequiredSettings.check(env));
        assertTrue(e.getMessage().contains("DB_USERNAME") && e.getMessage().contains("dev"), e.getMessage());
    }

    @Test
    void anEmptyPasswordIsAllowedBecauseALocalDatabaseMayHaveNone() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.datasource.username", "app")
                .withProperty("spring.datasource.password", "")
                .withProperty("app.jwt.secret", "x".repeat(40));
        assertTrue(RequiredSettings.missing(env).isEmpty());
        RequiredSettings.check(env);
    }
}
