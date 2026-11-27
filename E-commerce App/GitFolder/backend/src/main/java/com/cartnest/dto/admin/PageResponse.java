package com.cartnest.dto.admin;

import java.util.List;

public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
}
