package com.cartnest.dto;

import java.util.List;

/** Search-box suggestions while the user types. */
public record SuggestResponse(List<ProductCard> products, List<CategoryRef> categories, List<BrandRef> brands) {
}
