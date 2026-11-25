package com.csb.cinescope;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point of the CineScope backend.
 *
 * <p>The backend is a thin, cached, key-protecting layer between the React app and TMDB (The Movie Database):
 * <pre>
 * React --/api--> controller -> service -> tmdb gateway --HTTPS--> api.themoviedb.org
 *                    |            |            |
 *                   dto        mapper       cache (Caffeine)
 * </pre>
 * The TMDB token lives only on the server, never in the browser. {@code @ConfigurationPropertiesScan} registers
 * {@code config/TmdbProperties}; component scanning wires every other layer. Settings: application.properties.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CineScopeApplication {

    public static void main(String[] args) {
        SpringApplication.run(CineScopeApplication.class, args);
    }
}
