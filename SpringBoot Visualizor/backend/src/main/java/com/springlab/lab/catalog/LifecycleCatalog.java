package com.springlab.lab.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The REST Request Lifecycle module's catalogue, loaded once from {@code lab/lifecycle-catalog.json}.
 *
 * <p>It is the single source of truth for (a) which existing endpoints may be traced (an allow-list: tracing any other
 * path is silently ignored), (b) what each experiment changes and what it claims will happen, and (c) the
 * expectations the truthfulness tests execute against the real application.
 */
@Component
public class LifecycleCatalog {

    public record Expectation(Integer status, Boolean handlerInvoked, Integer sqlCount) {
    }

    /** A real, existing SpringLab endpoint that may be called with tracing enabled. GET only. */
    public record Target(String id, String method, String path, String query, String title, String description,
                         Map<String, Expectation> expectation) {
        public String url() {
            return query == null || query.isBlank() ? path : path + "?" + query;
        }
    }

    /**
     * A predefined What If. kind is LIVE (a real request variant is sent) or SIMULATED (explanation only).
     * variant (LIVE only): AS_IS, NO_TOKEN or TAMPERED_TOKEN. Nothing here can change server configuration.
     */
    public record Experiment(String id, String kind, String title, String target, String variant, String changes,
                             String consequence, String realBehaviourReference, String codeRefId,
                             Map<String, Expectation> expectation) {
    }

    public record Catalog(List<Target> targets, List<Experiment> experiments) {
    }

    private final Catalog catalog;

    public LifecycleCatalog(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource("lab/lifecycle-catalog.json").getInputStream()) {
            this.catalog = objectMapper.readValue(in, Catalog.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load lab/lifecycle-catalog.json", e);
        }
    }

    public Catalog catalog() {
        return catalog;
    }

    public Optional<Target> target(String id) {
        return catalog.targets().stream().filter(t -> t.id().equals(id)).findFirst();
    }

    /** Tracing is honoured only for GET requests to exactly one of the catalogued paths. */
    public boolean isTraceable(String method, String path) {
        return "GET".equals(method) && catalog.targets().stream().anyMatch(t -> t.path().equals(path));
    }

    /** Picks the expectation for a caller role, falling back to the "*" entry. */
    public static Optional<Expectation> expectationFor(Map<String, Expectation> expectations, String role) {
        if (expectations == null) {
            return Optional.empty();
        }
        Expectation specific = expectations.get(role);
        return Optional.ofNullable(specific != null ? specific : expectations.get("*"));
    }
}
