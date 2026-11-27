package com.cartnest.dto;

import jakarta.validation.constraints.Size;

public record CancelOrderRequest(@Size(max = 200, message = "reason must be at most 200 characters") String reason) {
}
