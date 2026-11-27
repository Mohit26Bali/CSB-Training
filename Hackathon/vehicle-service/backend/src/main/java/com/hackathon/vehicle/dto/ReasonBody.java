package com.hackathon.vehicle.dto;

import jakarta.validation.constraints.Size;

public record ReasonBody(@Size(max = 300, message = "Reason must be at most 300 characters") String reason) {
}
