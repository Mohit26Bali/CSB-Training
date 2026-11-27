package com.hackathon.hotel.dto;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import com.hackathon.hotel.entity.Room;

/** nights / totalPrice are only set when the room is shown for a particular stay. */
public record RoomResponse(Long id, String number, String type, int floor, BigDecimal pricePerNight, int capacity, String description,
                           List<String> amenities, boolean active, Integer nights, BigDecimal totalPrice) {

    public static RoomResponse from(Room r, Integer nights) {
        List<String> amenities = r.getAmenities() == null || r.getAmenities().isBlank() ? List.of()
                : Arrays.stream(r.getAmenities().split(",")).map(String::trim).filter(a -> !a.isEmpty()).toList();
        return new RoomResponse(r.getId(), r.getNumber(), r.getType().name(), r.getFloor(), r.getPricePerNight(), r.getCapacity(),
                r.getDescription(), amenities, r.isActive(), nights, nights == null ? null : r.getPricePerNight().multiply(BigDecimal.valueOf(nights)));
    }
}
