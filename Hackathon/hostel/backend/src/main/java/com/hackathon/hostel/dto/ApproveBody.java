package com.hackathon.hostel.dto;

import jakarta.validation.constraints.NotNull;

public record ApproveBody(@NotNull(message = "Choose a room to allocate") Long roomId) {
}
