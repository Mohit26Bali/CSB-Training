/**
 * The only package that talks to TMDB.
 *
 * <ul>
 *   <li>{@link com.csb.cinescope.tmdb.TmdbGateway} - what the rest of the application may ask TMDB for.</li>
 *   <li>{@link com.csb.cinescope.tmdb.TmdbHttpGateway} - the real implementation: HTTPS calls, caching, error mapping.</li>
 *   <li>{@link com.csb.cinescope.tmdb.TmdbQuery} - builds the (sorted, therefore cache-friendly) query parameters.</li>
 * </ul>
 * {@code service/CatalogService} depends on the interface only, so tests can replace TMDB with canned answers.
 */
package com.csb.cinescope.tmdb;
