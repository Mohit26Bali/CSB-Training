package com.icc.common.page;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * The shape of every list endpoint: the items of one page plus the total count (ARCHITECTURE, conventions).
 * The @NotNull annotations only describe the shape for the OpenAPI contract (D24); they do not change the JSON.
 */
public record PageResponse<T>(@NotNull List<T> items, @NotNull long total, @NotNull int page, @NotNull int size) {
}
