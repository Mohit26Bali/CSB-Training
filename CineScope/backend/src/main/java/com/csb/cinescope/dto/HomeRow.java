package com.csb.cinescope.dto;

import java.util.List;

/**
 * One horizontal row of the home page, for example "Popular movies". {@code category} and {@code mediaType} are the
 * values the "See all" link passes to {@code GET /api/titles?category=...&type=...} to show the full list.
 */
public record HomeRow(String key, String title, String category, String mediaType, List<TitleSummary> items) {
}
