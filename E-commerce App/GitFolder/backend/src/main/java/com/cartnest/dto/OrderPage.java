package com.cartnest.dto;

import java.util.List;

public record OrderPage(List<OrderSummaryResponse> items, int page, int size, long totalItems, int totalPages) {
}
