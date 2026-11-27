package com.hackathon.hostel.dto;

import jakarta.validation.constraints.Size;

/** A student's request for a room. */
public record RoomAskBody(String preferredType,
                          @Size(max = 300, message = "Note must be at most 300 characters") String note) {
}
