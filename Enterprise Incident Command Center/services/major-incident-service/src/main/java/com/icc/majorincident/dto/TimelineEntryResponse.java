package com.icc.majorincident.dto;

import com.icc.majorincident.entity.TimelineEntryType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record TimelineEntryResponse(@NotNull Long id, @NotNull TimelineEntryType entryType, @NotNull String text,
                                    @NotNull Long authorId, @NotNull LocalDateTime createdAt) {
}
