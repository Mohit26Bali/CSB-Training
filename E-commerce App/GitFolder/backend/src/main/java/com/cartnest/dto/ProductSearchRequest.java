package com.cartnest.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ProductSearchRequest(
        @Size(max = 80, message = "category is too long") String category,
        @Size(max = 100, message = "q must be at most 100 characters") String q,
        List<String> brand,
        @PositiveOrZero(message = "minPrice must be 0 or more") BigDecimal minPrice,
        @PositiveOrZero(message = "maxPrice must be 0 or more") BigDecimal maxPrice,
        @DecimalMin(value = "0", message = "minRating must be between 0 and 5")
        @DecimalMax(value = "5", message = "minRating must be between 0 and 5") Double minRating,
        @Min(value = 0, message = "minDiscount must be between 0 and 99")
        @Max(value = 99, message = "minDiscount must be between 0 and 99") Integer minDiscount,
        List<String> sizes,
        Boolean inStock,
        @Pattern(regexp = "relevance|popularity|price_asc|price_desc|newest|discount|rating",
                message = "sort must be one of relevance, popularity, price_asc, price_desc, newest, discount, rating")
        String sort,
        @Min(value = 0, message = "page must be 0 or more") Integer page,
        @Min(value = 1, message = "size must be between 1 and 60")
        @Max(value = 60, message = "size must be between 1 and 60") Integer size) {
    public static final int DEFAULT_PAGE_SIZE = 24;

    public int pageOrDefault() {
        return page == null ? 0 : page;
    }

    public int sizeOrDefault() {
        return size == null ? DEFAULT_PAGE_SIZE : size;
    }

    public boolean hasText() {
        return q != null && !q.isBlank();
    }

    public String sortOrDefault() {
        if (sort == null || ("relevance".equals(sort) && !hasText())) {
            return hasText() ? "relevance" : "popularity";
        }
        return sort;
    }
}
