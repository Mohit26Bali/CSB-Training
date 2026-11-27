package com.springlab.lab.jpa;

import com.springlab.lab.jpa.LabJpaDtos.ExperimentRun;
import com.springlab.lab.jpa.LabJpaDtos.Mapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The JPA Relationship Lab API. Every route is behind the application authentication (any signed-in role: it is learning
 * material). The only input is an experiment id from a fixed catalogue.
 */
@RestController
@RequestMapping("/api/lab/jpa")
public class LabJpaController {

    private final LabJpaCatalog catalog;
    private final JpaMappingService mappingService;
    private final JpaLabService labService;

    public LabJpaController(LabJpaCatalog catalog, JpaMappingService mappingService, JpaLabService labService) {
        this.catalog = catalog;
        this.mappingService = mappingService;
        this.labService = labService;
    }

    @GetMapping("/catalog")
    public LabJpaCatalog.Catalog catalog() {
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
