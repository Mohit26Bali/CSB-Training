package com.hackathon.helpdesk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StatusRequest(@NotBlank(message = "Choose a status") String status,
                            @Size(max = 1000, message = "Note must be at most 1000 characters") String note) {
}
