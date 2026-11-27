package com.hackathon.event.dto;

import java.time.LocalDateTime;

import com.hackathon.event.entity.Registration;

/** waitlistPosition is 1-based and only set for WAITLISTED registrations. */
public record RegistrationResponse(Long id, Long eventId, String eventTitle, String eventCategory, String venue,
                                   LocalDateTime startsAt, String eventStatus, String username, String fullname,
                                   String status, Integer waitlistPosition, LocalDateTime registeredAt) {

    public static RegistrationResponse from(Registration r, Integer waitlistPosition) {
        return new RegistrationResponse(r.getId(), r.getEvent().getId(), r.getEvent().getTitle(),
                r.getEvent().getCategory(), r.getEvent().getVenue(), r.getEvent().getStartsAt(),
                r.getEvent().getStatus().name(), r.getUser().getUsername(), r.getUser().getFullname(),
                r.getStatus().name(), waitlistPosition, r.getRegisteredAt());
    }
}
