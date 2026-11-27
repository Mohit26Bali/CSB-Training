package com.hackathon.leave.dto;

import jakarta.validation.constraints.Size;

/** The optional message a manager attaches when approving or rejecting. */
public record DecisionNote(@Size(max = 255, message = "Note must be at most 255 characters") String note) {
}
