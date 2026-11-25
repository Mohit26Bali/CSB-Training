package com.csb.banking.dto;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Stable JSON wrapper for any paged list (used for transaction history), so the API does not leak Spring Data's
 * internal {@code Page} structure. {@code page} is zero-based.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
