/**
 * The REST layer: translates HTTP into {@code service/} calls. No business rules here.
 *
 * <pre>
 * CatalogController    /api/home, /api/titles, /api/discover, /api/search, /api/genres   public, movie and TV data
 * AuthController       /api/auth/register, /login, /me                                  accounts
 * WatchlistController  /api/watchlist/**                                                login required
 * MetaController       /api/meta                                                        diagnostics (database kind)
 * </pre>
 * Each controller hands {@code dto/} request records (validated with {@code @Valid}) to one {@code service/} class and
 * returns {@code dto/} responses. Errors are handled centrally by {@code exception/GlobalExceptionHandler}.
 */
package com.csb.cinescope.controller;
