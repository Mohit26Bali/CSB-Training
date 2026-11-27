package com.springlab.lab.jdbc;

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
 * The JDBC Lab catalogue ({@code lab/jdbc-lab-catalog.json}): the topics, the experiments (the question each asks and the outcome
 * it claims) and the diagram each experiment's steps refer to. The integration tests run every experiment against MySQL and
 * compare the observed behaviour with these claims.
 */
@Component
public class JdbcLabCatalog {

    public record Group(String id, String title, String description) {
    }

    public record ExperimentInfo(String id, String group, String title, String question, String claim, String diagram) {
    }

    public record Diagram(List<String> nodes, List<String> edges) {
    }

    public record Catalog(List<Group> groups, List<ExperimentInfo> experiments, Map<String, Diagram> diagrams, List<String> limitations) {
    }

    private final Catalog catalog;

    public JdbcLabCatalog(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource("lab/jdbc-lab-catalog.json").getInputStream()) {
            this.catalog = objectMapper.readValue(in, Catalog.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load lab/jdbc-lab-catalog.json", e);
        }
    }

    public Catalog catalog() {
        return catalog;
    }

    public Optional<ExperimentInfo> find(String id) {
        return catalog.experiments().stream().filter(e -> e.id().equals(id)).findFirst();
    }
}
