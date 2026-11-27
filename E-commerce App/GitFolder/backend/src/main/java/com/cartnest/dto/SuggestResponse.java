package com.cartnest.dto;

import java.util.List;

public record SuggestResponse(List<ProductCard> products, List<CategoryRef> categories, List<BrandRef> brands) {
}
