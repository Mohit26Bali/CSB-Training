package com.hackathon.complaints.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** note: the resolution notes when resolving (mandatory), otherwise an optional remark shown on the timeline. */
public record StatusRequest(@NotBlank(message = "Choose a status") String status,
                            @Size(max = 1000, message = "Note must be at most 1000 characters") String note) {
}
