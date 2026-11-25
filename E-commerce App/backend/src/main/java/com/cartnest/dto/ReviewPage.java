package com.cartnest.dto;

import java.util.List;

public record ReviewPage(List<ReviewResponse> items, int page, int size, long totalItems, int totalPages,
        RatingSummary summary) {
}
