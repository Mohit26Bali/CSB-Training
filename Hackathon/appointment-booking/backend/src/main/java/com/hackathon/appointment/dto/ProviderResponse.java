package com.hackathon.appointment.dto;

import java.time.LocalDateTime;

import com.hackathon.appointment.entity.Provider;

public record ProviderResponse(Long id, String name, String title, String description, String location, int freeSlots, LocalDateTime nextFree) {

    public static ProviderResponse of(Provider p, int freeSlots, LocalDateTime nextFree) {
        return new ProviderResponse(p.getId(), p.getUser().getFullname(), p.getTitle(), p.getDescription(), p.getLocation(), freeSlots, nextFree);
    }
}
