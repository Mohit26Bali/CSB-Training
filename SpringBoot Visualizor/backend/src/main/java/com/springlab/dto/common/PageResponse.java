package com.springlab.dto.common;

import org.springframework.data.domain.Page;

import java.util.List;

/** Stable JSON shape for pages (serializing Spring's PageImpl directly is not a supported, stable format). */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }
}
