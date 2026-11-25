package com.cartnest.dto.admin;

import java.util.List;

import com.cartnest.entity.VariantType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Creates or replaces a product. Variants with an id are updated, without one are added, missing ones are removed. */
public record ProductUpsertRequest(@NotBlank(message = "Title is required") @Size(max = 150, message = "Title must be at most 150 characters") String title,
        @NotBlank(message = "Description is required") @Size(max = 2000, message = "Description must be at most 2000 characters") String description,
        @NotNull(message = "categoryId is required") Long categoryId,
        Long brandId,
        @NotNull(message = "variantType is required (NONE, SIZE, STORAGE or CONFIGURATION)") VariantType variantType,
        @NotEmpty(message = "At least one image is required") @Size(max = 10, message = "At most 10 images")
        List<@NotBlank @Size(max = 500) @Pattern(regexp = "(https?://|/)\\S+", message = "Each image must be an http(s) URL or a site path") String> images,
        @NotEmpty(message = "At least one variant is required") @Size(max = 20, message = "At most 20 variants")
        List<@Valid VariantInput> variants,
        @Size(max = 20, message = "At most 20 specifications") List<@Valid SpecInput> specs,
        @Size(max = 10, message = "At most 10 tags") List<@NotBlank @Size(max = 60) String> tags,
        @Size(max = 100) String warranty, @Size(max = 100) String shippingInfo, @Size(max = 100) String returnPolicy,
        Boolean active) {
}
