/**
 * CineScope's own JSON shapes: what the React app receives.
 *
 * <p>TMDB's answers are large and inconsistent (movies have {@code title}, shows have {@code name}, dates have
 * different names, many fields are null). {@code mapper/TitleMapper} converts them into these small, uniform records, so
 * the frontend never has to know TMDB's quirks. Image fields hold TMDB image <i>paths</i> such as
 * {@code /abc123.jpg}; the frontend adds the base URL and size (see frontend/src/lib/images.ts).
 */
package com.csb.cinescope.dto;
