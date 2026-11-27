package com.hackathon.event.dto;

import jakarta.validation.constraints.NotNull;

/** joinWaitlist: only used when the event is full; without it a full event is refused. */
public record RegistrationRequest(@NotNull(message = "Choose an event") Long eventId, Boolean joinWaitlist) {
}
