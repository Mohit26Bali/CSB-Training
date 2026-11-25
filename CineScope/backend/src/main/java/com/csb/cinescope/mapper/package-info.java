/**
 * Converts TMDB's JSON into CineScope's {@code dto/} records. Called by {@code service/CatalogService}; contains every
 * TMDB-specific quirk so that nothing else in the application needs to know the shape of TMDB's answers.
 */
package com.csb.cinescope.mapper;
