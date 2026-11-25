/**
 * The use cases of the application.
 *
 * <ul>
 *   <li>{@code CatalogService} - validates what may be asked, fetches TMDB data through {@code tmdb/TmdbGateway} and converts
 *       it with {@code mapper/TitleMapper}.</li>
 *   <li>{@code AuthService}, {@code LoginAttemptService}, {@code CustomUserDetailsService} - accounts and login.</li>
 *   <li>{@code WatchlistService} - a user's personal list; asks {@code CatalogService} to verify and snapshot a title.</li>
 * </ul>
 * Later phases add where-to-watch, people and recommendations next to them.
 */
package com.csb.cinescope.service;
