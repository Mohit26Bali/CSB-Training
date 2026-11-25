/**
 * Business and upstream failures and their translation to HTTP.
 *
 * <ul>
 *   <li>{@code tmdb/} and {@code service/} throw {@link com.csb.cinescope.exception.AppException} subclasses.</li>
 *   <li>Each carries the HTTP status it should produce; {@link com.csb.cinescope.exception.GlobalExceptionHandler}
 *       turns them (and Spring's own request errors) into one JSON error shape, so controllers need no try/catch.</li>
 * </ul>
 */
package com.csb.cinescope.exception;
