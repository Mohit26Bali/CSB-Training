package com.springlab.lab.jdbc;

import com.springlab.lab.jdbc.JdbcLabDtos.ExperimentRun;
import com.springlab.lab.jdbc.JdbcLabDtos.Mapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The JDBC Lab API. Every route is behind the application authentication (any signed-in role: it is learning material). The only
 * input is an experiment id from a fixed catalogue.
 */
@RestController
@RequestMapping("/api/lab/jdbc")
public class LabJdbcController {

    private final JdbcLabCatalog catalog;
    private final JdbcMappingService mappingService;
    private final JdbcLabService labService;

    public LabJdbcController(JdbcLabCatalog catalog, JdbcMappingService mappingService, JdbcLabService labService) {
        this.catalog = catalog;
        this.mappingService = mappingService;
        this.labService = labService;
    }

    @GetMapping("/catalog")
    public JdbcLabCatalog.Catalog catalog() {
        return catalog.catalog();
    }

    @GetMapping("/mapping")
    public Mapping mapping() {
        return mappingService.mapping();
    }

    @PostMapping("/experiments/{id}/run")
    public ExperimentRun run(@PathVariable String id) {
        return labService.run(id);
    }
}
