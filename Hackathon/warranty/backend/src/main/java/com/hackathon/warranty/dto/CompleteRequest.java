package com.hackathon.warranty.dto;

import jakarta.validation.constraints.Size;

/** The optional note the agent leaves when the repair is finished. */
public record CompleteRequest(@Size(max = 500, message = "Note must be at most 500 characters") String resolutionNote) {
}
