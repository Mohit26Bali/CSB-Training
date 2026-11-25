package com.csb.cinescope.dto;

/** Harmless diagnostic facts about the running backend ({@code GET /api/meta}): the application name and the kind of database it is using. */
public record MetaResponse(String application, String database) {
}
