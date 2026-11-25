package com.csb.cinescope;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Replaces the real TMDB HTTP client with one that talks to a mock server, in any API test that imports it (see
 * {@code AbstractApiTest}). Tests set expectations on the {@link MockRestServiceServer} bean ("the backend will call
 * /movie/550, answer with this JSON"), so nothing ever reaches themoviedb.org and no real token is needed.
 *
 * <p>The mock accepts expected requests in any order, because the home page fetches its rows in parallel.
 */
@TestConfiguration
public class FakeTmdbConfig {

    public static final String TOKEN = "test-token-123";
    public static final String BASE = "http://tmdb.test/3";

    // The server must be bound to the builder BEFORE the client is built from it.
    private final RestClient.Builder builder = RestClient.builder().baseUrl(BASE).defaultHeader("Authorization", "Bearer " + TOKEN);
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();

    @Bean
    MockRestServiceServer mockTmdb() {
        return server;
    }

    /** {@code @Primary}: the gateway receives this client instead of the real one built by config/TmdbClientConfig. */
    @Bean
    @Primary
    RestClient fakeTmdbRestClient() {
        return builder.build();
    }
}
