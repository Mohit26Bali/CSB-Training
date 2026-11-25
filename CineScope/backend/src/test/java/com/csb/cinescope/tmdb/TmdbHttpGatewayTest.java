package com.csb.cinescope.tmdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestClient;

import com.csb.cinescope.exception.NotFoundException;
import com.csb.cinescope.exception.UpstreamException;

import tools.jackson.databind.JsonNode;

/**
 * Unit tests of {@link TmdbHttpGateway} against a mock HTTP server (no network): what it sends (URL, sorted and
 * encoded parameters, the secret bearer header) and how every kind of TMDB failure is translated into one of our own
 * exceptions. Caching is tested in {@code controller/CatalogApiTest}, which runs with the real cache proxy.
 */
class TmdbHttpGatewayTest {

    private static final String BASE = "http://tmdb.test/3";

    MockRestServiceServer server;
    TmdbGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE).defaultHeader("Authorization", "Bearer secret-token");
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new TmdbHttpGateway(builder.build());
    }

    @Test
    void sendsTheBearerTokenAndTheSortedParametersAndReturnsTheJson() {
        server.expect(requestTo(BASE + "/movie/550?append_to_response=credits&language=en-US"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer secret-token"))
                .andRespond(withSuccess("{\"id\":550,\"title\":\"Fight Club\"}", MediaType.APPLICATION_JSON));

        JsonNode result = gateway.detail("/movie/550",
                TmdbQuery.create().with("language", "en-US").with("append_to_response", "credits").build());

        assertEquals("Fight Club", result.path("title").asString(""));
        server.verify();
    }

    @Test
    void parametersAreUrlEncoded() {
        server.expect(requestTo(BASE + "/search/multi?query=the%20dark%20knight%20%26%20co"))
                .andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));

        gateway.lists("/search/multi", TmdbQuery.create().with("query", "the dark knight & co").build());

        server.verify();
    }

    @Test
    void blankParametersAreLeftOut() {
        assertEquals(1, TmdbQuery.create().with("a", "x").with("b", " ").with("c", null).build().size());
        assertFalse(TmdbQuery.create().with("b", "").build().containsKey("b"));
    }

    @Test
    void aTmdb404BecomesNotFound() {
        server.expect(requestTo(BASE + "/movie/1")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(NotFoundException.class, () -> gateway.detail("/movie/1", TmdbQuery.create().build()));
    }

    @Test
    void rateLimitingBecomes503() {
        server.expect(requestTo(BASE + "/movie/popular")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        UpstreamException ex = assertThrows(UpstreamException.class, () -> gateway.lists("/movie/popular", TmdbQuery.create().build()));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
    }

    @Test
    void aRejectedTokenBecomes502WithoutLeakingAnyDetails() {
        server.expect(requestTo(BASE + "/movie/popular"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"status_message\":\"Invalid API key: secret-token\"}"));

        UpstreamException ex = assertThrows(UpstreamException.class, () -> gateway.lists("/movie/popular", TmdbQuery.create().build()));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
        assertFalse(ex.getMessage().contains("secret-token"));
    }

    @Test
    void aTmdbServerErrorBecomes502() {
        server.expect(requestTo(BASE + "/movie/popular")).andRespond(withServerError());

        assertEquals(HttpStatus.BAD_GATEWAY,
                assertThrows(UpstreamException.class, () -> gateway.lists("/movie/popular", TmdbQuery.create().build())).getStatus());
    }

    @Test
    void aTimeoutBecomes504WithoutBeingRepeated() {
        server.expect(requestTo(BASE + "/movie/popular")).andRespond(MockRestResponseCreators.withException(new java.net.SocketTimeoutException("Read timed out")));

        assertEquals(HttpStatus.GATEWAY_TIMEOUT,
                assertThrows(UpstreamException.class, () -> gateway.lists("/movie/popular", TmdbQuery.create().build())).getStatus());
        server.verify(); // exactly the one request: the caller already waited the full read timeout
    }

    @Test
    void aFailedConnectionIsRetriedOnceAndTheSecondAttemptCanSucceed() {
        server.expect(requestTo(BASE + "/movie/popular")).andRespond(MockRestResponseCreators.withException(new IOException("Remote host terminated the handshake")));
        server.expect(requestTo(BASE + "/movie/popular")).andRespond(withSuccess("{\"page\":1}", MediaType.APPLICATION_JSON));

        assertEquals(1, gateway.lists("/movie/popular", TmdbQuery.create().build()).path("page").asInt());
        server.verify();
    }

    @Test
    void aConnectionThatKeepsFailingIsRetriedOnlyOnceThenBecomes504() {
        server.expect(requestTo(BASE + "/movie/popular")).andRespond(MockRestResponseCreators.withException(new IOException("Remote host terminated the handshake")));
        server.expect(requestTo(BASE + "/movie/popular")).andRespond(MockRestResponseCreators.withException(new IOException("Remote host terminated the handshake")));

        assertEquals(HttpStatus.GATEWAY_TIMEOUT,
                assertThrows(UpstreamException.class, () -> gateway.lists("/movie/popular", TmdbQuery.create().build())).getStatus());
        server.verify(); // two requests in total, never a third
    }

    @Test
    void anHttpErrorAnswerIsNeverRetried() {
        server.expect(requestTo(BASE + "/movie/popular")).andRespond(withServerError());

        assertThrows(UpstreamException.class, () -> gateway.lists("/movie/popular", TmdbQuery.create().build()));
        server.verify();
    }

    @Test
    void anEmptyBodyIsTreatedAsAnUpstreamError() {
        server.expect(requestTo(BASE + "/movie/popular")).andRespond(withSuccess());

        assertEquals(HttpStatus.BAD_GATEWAY,
                assertThrows(UpstreamException.class, () -> gateway.lists("/movie/popular", TmdbQuery.create().build())).getStatus());
    }
}
