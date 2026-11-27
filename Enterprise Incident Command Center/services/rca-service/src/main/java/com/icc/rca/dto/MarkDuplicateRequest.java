package com.icc.rca.dto;

import jakarta.validation.constraints.NotNull;

/** The original problem this one duplicates. */
public record MarkDuplicateRequest(@NotNull Long duplicateOfId) {
}
