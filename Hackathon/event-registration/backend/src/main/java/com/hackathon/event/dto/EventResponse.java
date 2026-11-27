package com.hackathon.event.dto;

import java.time.LocalDateTime;

import com.hackathon.event.entity.Event;

/** myStatus / myRegistrationId describe the signed-in employee's own registration (null when there is none). */
public record EventResponse(Long id, String title, String category, String venue, String description,
                            LocalDateTime startsAt, LocalDateTime endsAt, int capacity, String status, long confirmed,
                            long waitlisted, long spotsLeft, boolean past, String myStatus, Long myRegistrationId) {

    public static EventResponse from(Event e, long confirmed, long waitlisted, String myStatus, Long myRegistrationId,
                                     LocalDateTime now) {
        return new EventResponse(e.getId(), e.getTitle(), e.getCategory(), e.getVenue(), e.getDescription(),
                e.getStartsAt(), e.getEndsAt(), e.getCapacity(), e.getStatus().name(), confirmed, waitlisted,
                Math.max(0, e.getCapacity() - confirmed), e.hasStarted(now), myStatus, myRegistrationId);
    }
}
