package com.cartnest.dto;

import java.util.List;

public record ProductPage(List<ProductCard> items, int page, int size, long totalItems, int totalPages, String sort,
        SearchFacets facets) {
}
