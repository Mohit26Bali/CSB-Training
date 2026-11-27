package com.icc.majorincident.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddTimelineEntryRequest(@NotBlank @Size(max = 4000) String text) {
}
