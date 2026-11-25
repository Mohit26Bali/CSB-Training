package com.cartnest.dto.admin;

import java.util.List;

/** One page of any list. */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
}
