package com.hackathon.asset.dto;

import jakarta.validation.constraints.Size;

public record ReturnBody(@Size(max = 300, message = "Note must be at most 300 characters") String note) {
}
