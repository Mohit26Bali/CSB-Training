/**
 * JPA entities: the Java shape of the tables in the MySQL "cinescope" database.
 *
 * <pre>
 * users 1 --- * watchlist_items
 * </pre>
 * <ul>
 *   <li>Read and written only through {@code repository/} interfaces.</li>
 *   <li>Never returned from controllers; {@code service/} converts them to {@code dto/} records.</li>
 *   <li>Movies and shows themselves are NOT stored: they live at TMDB. A watchlist row keeps only the TMDB id plus a small
 *       snapshot (title, poster, year) so the watchlist page needs no TMDB request per item.</li>
 * </ul>
 */
package com.csb.cinescope.entity;
