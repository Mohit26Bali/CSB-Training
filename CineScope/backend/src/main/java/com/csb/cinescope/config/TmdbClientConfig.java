package com.csb.cinescope.config;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * Builds the two infrastructure beans the TMDB layer needs.
 *
 * <ul>
 *   <li>{@code tmdbRestClient}: a {@link RestClient} that already knows TMDB's base URL, sends the secret as
 *       {@code Authorization: Bearer <token>} (header, so it never appears in URLs or logs) and has connect / read
 *       timeouts, so a slow TMDB cannot hang our threads. Used only by {@code tmdb/TmdbHttpGateway}.</li>
 *   <li>{@code catalogExecutor}: a small thread pool on which {@code service/CatalogService} fetches the several TMDB
 *       lists of the home page in parallel instead of one after another.</li>
 * </ul>
 */
@Configuration
public class TmdbClientConfig {

    @Bean
    public RestClient tmdbRestClient(TmdbProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.connectTimeoutMs()))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.readToken())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService catalogExecutor() {
        AtomicInteger counter = new AtomicInteger();
        return Executors.newFixedThreadPool(8, runnable -> {
            Thread thread = new Thread(runnable, "catalog-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }
}
