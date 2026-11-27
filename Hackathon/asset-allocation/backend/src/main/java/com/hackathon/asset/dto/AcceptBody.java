package com.hackathon.asset.dto;

import com.hackathon.asset.entity.AssetCondition;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AcceptBody(
        @NotNull(message = "Record the condition it came back in") AssetCondition condition,
        @Size(max = 300, message = "Note must be at most 300 characters") String note) {
}
