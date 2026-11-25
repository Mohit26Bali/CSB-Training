package com.csb.cinescope.config;

import java.time.Duration;
import java.util.List;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * In-memory caches for TMDB answers, with a lifetime that fits how fast each kind of data changes.
 *
 * <p>Used through {@code @Cacheable} on {@code tmdb/TmdbHttpGateway}. Why cache at all: TMDB limits request rates, and
 * the same home-page lists are requested by every visitor, so caching makes pages instant and keeps us far below the
 * limit. Entries live in this JVM only; restarting the backend empties them (a shared cache such as Redis would be
 * needed to run several instances).
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** Trending / popular / search: changes through the day. */
    public static final String LISTS = "tmdb-lists";
    /** One movie / TV show / person with credits: changes rarely. */
    public static final String DETAILS = "tmdb-details";
    /** Genre lists and other reference data: practically static. */
    public static final String REFERENCE = "tmdb-reference";

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                cache(LISTS, Duration.ofMinutes(10), 2_000),
                cache(DETAILS, Duration.ofHours(1), 2_000),
                cache(REFERENCE, Duration.ofHours(24), 50)));
        return manager;
    }

    private static CaffeineCache cache(String name, Duration timeToLive, long maxEntries) {
        return new CaffeineCache(name, Caffeine.newBuilder().expireAfterWrite(timeToLive).maximumSize(maxEntries).build());
    }
}
