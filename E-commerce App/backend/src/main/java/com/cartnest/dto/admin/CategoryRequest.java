package com.cartnest.dto.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** parentId is only used when creating; categories are not moved between parents. */
public record CategoryRequest(@NotBlank(message = "Name is required") @Size(max = 80, message = "Name must be at most 80 characters") String name,
        Long parentId,
        @Size(max = 500) @Pattern(regexp = "(https?://|/)\\S+", message = "Image must be an http(s) URL or a site path") String imageUrl,
        @Min(value = 0, message = "sortOrder cannot be negative") Integer sortOrder) {
}
