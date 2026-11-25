package com.csb.cinescope.controller;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.csb.cinescope.dto.MetaResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * {@code GET /api/meta}: which application this is and what kind of database it is running on ("MySQL" or "H2"). Public and
 * harmless (no versions, hosts or data). It exists so that the browser-test setup (frontend/e2e/global-setup.ts) can verify
 * that the backend under test is on the throw-away in-memory database before any test creates users.
 */
@Tag(name = "Diagnostics")
@RestController
@RequestMapping("/api")
public class MetaController {

    private final DataSource dataSource;

    public MetaController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Operation(summary = "Application and database kind")
    @SecurityRequirements
    @GetMapping("/meta")
    public MetaResponse meta() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return new MetaResponse("CineScope", connection.getMetaData().getDatabaseProductName());
        }
    }
}
