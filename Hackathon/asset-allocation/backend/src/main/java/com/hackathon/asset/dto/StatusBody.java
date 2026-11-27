package com.hackathon.asset.dto;

import com.hackathon.asset.entity.AssetStatus;

import jakarta.validation.constraints.NotNull;

public record StatusBody(@NotNull(message = "Choose a status") AssetStatus status) {
}
