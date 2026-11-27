package com.springlab.lab.jwt;

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
 * The JWT Security Simulator's catalogue ({@code lab/jwt-simulator-catalog.json}): the lab endpoints that may be traced,
 * the token faults a learner can issue, and the predefined experiments with the outcomes the lesson claims. The
 * truthfulness tests execute every experiment against the real application and compare with these expectations.
 */
@Component
public class JwtSimulatorCatalog {

    public record Target(String id, String method, String path, String title, String description, String requires) {
    }

    /** issued=false means the client sends no token at all (NO_TOKEN). failingStage is the check the lesson says fails. */
    public record Variant(String id, String title, String description, boolean issued, String failingStage) {
    }

    public record Expectation(Integer status, Boolean handlerInvoked, Integer sqlCount, String challengeContains) {
    }

    public record Experiment(String id, String kind, String group, String title, String target, String variant,
                             String claimedRole,
                             String changes, String consequence, String realBehaviourReference, String codeRefId,
                             Map<String, Expectation> expectation) {
    }

    public record Catalog(List<Target> targets, List<Variant> variants, List<Experiment> experiments) {
    }

    private final Catalog catalog;

    public JwtSimulatorCatalog(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource("lab/jwt-simulator-catalog.json").getInputStream()) {
            this.catalog = objectMapper.readValue(in, Catalog.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load lab/jwt-simulator-catalog.json", e);
        }
    }

    public Catalog catalog() {
        return catalog;
    }

    public Optional<Target> target(String id) {
        return catalog.targets().stream().filter(t -> t.id().equals(id)).findFirst();
    }

    public boolean isTraceable(String method, String path) {
        return "GET".equals(method) && catalog.targets().stream().anyMatch(t -> t.path().equals(path));
    }
}
