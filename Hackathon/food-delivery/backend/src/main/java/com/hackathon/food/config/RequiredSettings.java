package com.hackathon.food.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.core.env.Environment;

/**
 * Stops the application at startup, with a message that names what is missing, instead of failing later with a database
 * connection error. The credentials and the JWT key have no defaults in application.properties; the `dev` profile supplies local ones.
 */
public final class RequiredSettings {

    /** property to read, and the environment variable a person would set to provide it */
    private static final Map<String, String> REQUIRED = Map.of(
            "spring.datasource.username", "DB_USERNAME",
            "spring.datasource.password", "DB_PASSWORD",
            "app.jwt.secret", "JWT_SECRET");

    private RequiredSettings() {
    }

    /** The environment variables that are missing, in a stable order. Empty when everything is set. */
    public static List<String> missing(Environment environment) {
        List<String> missing = new ArrayList<>();
        REQUIRED.forEach((property, variable) -> {
            String value;
            try {
                value = environment.getProperty(property);
            } catch (IllegalArgumentException unresolvedPlaceholder) {
                value = null;      // "${JWT_SECRET}" with no JWT_SECRET set
            }
            if (value == null || (value.startsWith("${") && value.endsWith("}"))) {
                missing.add(variable);
            }
        });
        missing.sort(String::compareTo);
        return missing;
    }

    public static void check(Environment environment) {
        List<String> missing = missing(environment);
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Missing required setting(s): " + String.join(", ", missing)
                    + ". Set them as environment variables, or for local development run with the `dev` profile "
                    + "(run-dev.cmd / run-dev.sh) after setting DB_PASSWORD. See the README, section Quick start.");
        }
    }
}
