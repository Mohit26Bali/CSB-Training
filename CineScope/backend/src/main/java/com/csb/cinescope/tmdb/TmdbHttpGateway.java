package com.csb.cinescope.tmdb;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

import com.csb.cinescope.config.CacheConfig;
import com.csb.cinescope.exception.NotFoundException;
import com.csb.cinescope.exception.UpstreamException;

import tools.jackson.databind.JsonNode;

/**
 * The real {@link TmdbGateway}: calls api.themoviedb.org with the {@code tmdbRestClient} from
 * {@code config/TmdbClientConfig} (which adds the secret bearer token) and caches successful answers
 * ({@code @Cacheable}, lifetimes in {@code config/CacheConfig}; failures are never cached).
 *
 * <p>Error policy, so callers see only our own exceptions: TMDB 404 becomes {@link NotFoundException}; 429 (rate
 * limit) becomes 503; 401 / 403 (bad token) and other TMDB errors become 502 with a neutral message (details only in
 * the log); network trouble or a timeout becomes 504.
 */
@Component
public class TmdbHttpGateway implements TmdbGateway {

    private static final long RETRY_PAUSE_MS = 300;

    private static final Logger log = LoggerFactory.getLogger(TmdbHttpGateway.class);

    private final RestClient client;

    public TmdbHttpGateway(RestClient tmdbRestClient) {
        this.client = tmdbRestClient;
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.LISTS, key = "#path + '?' + #query")
    public JsonNode lists(String path, SortedMap<String, String> query) {
        return fetch(path, query);
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.DETAILS, key = "#path + '?' + #query")
    public JsonNode detail(String path, SortedMap<String, String> query) {
        return fetch(path, query);
    }

    @Override
    @Cacheable(cacheNames = CacheConfig.REFERENCE, key = "#path + '?' + #query")
    public JsonNode reference(String path, SortedMap<String, String> query) {
        return fetch(path, query);
    }

    private JsonNode fetch(String path, SortedMap<String, String> query) {
        // Values are passed as URI variables so RestClient encodes each exactly once (building an already-encoded
        // string here would be encoded a second time: "a b" would reach TMDB as "a%20b").
        Map<String, String> variables = new LinkedHashMap<>();
        int index = 0;
        for (String value : query.values()) {
            variables.put("v" + index++, value);
        }
        Function<UriBuilder, URI> uri = builder -> {
            builder.path(path);
            int i = 0;
            for (String name : query.keySet()) {
                builder.queryParam(name, "{v" + i++ + "}");
            }
            return builder.build(variables);
        };
        try {
            JsonNode body = getWithOneRetry(path, uri);
            if (body == null) {
                throw new UpstreamException(HttpStatus.BAD_GATEWAY, "TMDB returned an empty answer");
            }
            return body;
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            log.warn("TMDB {} answered {}", path, status);
            if (status == 404) {
                throw new NotFoundException("Not found");
            }
            if (status == 429) {
                throw new UpstreamException(HttpStatus.SERVICE_UNAVAILABLE, "TMDB is busy right now. Please try again in a moment");
            }
            throw new UpstreamException(HttpStatus.BAD_GATEWAY, "TMDB could not be reached right now. Please try again later");
        } catch (ResourceAccessException ex) {
            log.warn("TMDB {} unreachable: {}", path, ex.getMessage());
            throw new UpstreamException(HttpStatus.GATEWAY_TIMEOUT, "TMDB took too long to answer. Please try again");
        }
    }

    /**
     * One GET, repeated once after a short pause when the connection itself failed (for example "Remote host terminated
     * the handshake", which some networks cause now and then and which clears at once). Safe because the request only
     * reads. A real timeout is not repeated: the caller already waited the full read timeout, and waiting twice as long
     * would not make TMDB faster. HTTP error answers are never repeated either (they come from RestClientResponseException).
     */
    private JsonNode getWithOneRetry(String path, Function<UriBuilder, URI> uri) {
        try {
            return client.get().uri(uri).retrieve().body(JsonNode.class);
        } catch (ResourceAccessException ex) {
            if (isTimeout(ex)) {
                throw ex;
            }
            log.info("TMDB {} connection failed ({}); trying once more", path, ex.getMessage());
            try {
                Thread.sleep(RETRY_PAUSE_MS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw ex;
            }
            return client.get().uri(uri).retrieve().body(JsonNode.class);
        }
    }

    private static boolean isTimeout(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof java.net.SocketTimeoutException || t instanceof java.net.http.HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
