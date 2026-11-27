package com.hackathon.parking.dto;

import jakarta.validation.constraints.NotNull;

public record ApproveBody(@NotNull(message = "Choose a slot to allocate") Long slotId) {
}
