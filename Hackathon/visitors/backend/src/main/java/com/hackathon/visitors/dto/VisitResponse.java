package com.hackathon.visitors.dto;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.hackathon.visitors.entity.Visit;
import com.hackathon.visitors.entity.VisitStatus;

/**
 * lapsed: the visitor never came and the day has passed. dueToday: expected today and not yet in.
 * canCheckIn / canCheckOut / canCancel tell the UI which buttons make sense. minutesInside is set while the visitor is in the building.
 */
public record VisitResponse(Long id, String passCode, String visitorName, String phone, String company, String purpose,
                            LocalDate expectedDate, LocalTime expectedTime, String status, String hostUsername, String hostName,
                            LocalDateTime createdAt, LocalDateTime checkedInAt, LocalDateTime checkedOutAt, String checkedInBy,
                            String checkedOutBy, boolean lapsed, boolean dueToday, boolean canCheckIn, boolean canCheckOut,
                            boolean canCancel, Long minutesInside) {

    public static VisitResponse from(Visit v, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        boolean expected = v.getStatus() == VisitStatus.EXPECTED;
        boolean inside = v.getStatus() == VisitStatus.CHECKED_IN;
        return new VisitResponse(v.getId(), v.getPassCode(), v.getVisitorName(), v.getPhone(), v.getCompany(), v.getPurpose(),
                v.getExpectedDate(), v.getExpectedTime(), v.getStatus().name(), v.getHost().getUsername(), v.getHost().getFullname(),
                v.getCreatedAt(), v.getCheckedInAt(), v.getCheckedOutAt(), v.getCheckedInBy(), v.getCheckedOutBy(),
                expected && v.getExpectedDate().isBefore(today), expected && v.getExpectedDate().equals(today),
                expected && v.getExpectedDate().equals(today), inside, expected,
                inside ? Duration.between(v.getCheckedInAt(), now).toMinutes() : null);
    }
}
