package com.springlab.lab.jpa;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;

/**
 * The JPA Relationship Lab catalogue ({@code lab/jpa-lab-catalog.json}): the experiment groups and the experiments with the
 * question each one asks and the outcome the lesson claims. The integration tests run every experiment against MySQL and
 * check the claims against what was observed, so the text cannot drift from the behaviour.
 */
@Component
public class LabJpaCatalog {

    public record Group(String id, String title, String description) {
    }

    /** diagram is "er" (entities and tables) or "flow" (application, EntityManager, persistence context, database). */
    public record ExperimentInfo(String id, String group, String title, String question, String claim, String diagram,
                                 List<String> focus) {
    }

    public record Catalog(List<Group> groups, List<ExperimentInfo> experiments, List<String> flowNodes, List<String> limitations) {
    }

    private final Catalog catalog;

    public LabJpaCatalog(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource("lab/jpa-lab-catalog.json").getInputStream()) {
            this.catalog = objectMapper.readValue(in, Catalog.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load lab/jpa-lab-catalog.json", e);
        }
    }

    public Catalog catalog() {
        return catalog;
    }

    public Optional<ExperimentInfo> find(String id) {
        return catalog.experiments().stream().filter(e -> e.id().equals(id)).findFirst();
    }

    public List<String> limitations() {
        return catalog.limitations();
    }
}
