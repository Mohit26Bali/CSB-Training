package com.cartnest.dto.admin;

public record AdminCategoryResponse(Long id, String name, String slug, Long parentId, String imageUrl, int sortOrder) {
}
