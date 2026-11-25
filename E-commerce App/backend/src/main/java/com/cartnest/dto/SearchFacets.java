package com.cartnest.dto;

import java.util.List;

/** Filter options for the current category / search text, so the sidebar shows only what exists. */
public record SearchFacets(List<FacetOption> categories, List<FacetOption> brands, PriceRange price,
        List<FacetOption> sizes, List<FacetOption> ratings, List<FacetOption> discounts) {
}
