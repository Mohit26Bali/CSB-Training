package com.cartnest.dto;

import java.util.List;

public record SearchFacets(List<FacetOption> categories, List<FacetOption> brands, PriceRange price,
        List<FacetOption> sizes, List<FacetOption> ratings, List<FacetOption> discounts) {
}
