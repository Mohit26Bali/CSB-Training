package com.hackathon.hostel.dto;

import java.util.List;

import com.hackathon.hostel.entity.Room;

/** occupants is only filled in for the admin. */
public record RoomResponse(Long id, String number, int floor, String type, int capacity, int occupied, int bedsLeft,
                           boolean active, List<String> occupants) {

    public static RoomResponse from(Room r, List<String> occupants) {
        return from(r, occupants.size(), occupants);
    }

    public static RoomResponse from(Room r, int occupied, List<String> occupants) {
        return new RoomResponse(r.getId(), r.getNumber(), r.getFloor(), r.getType().name(), r.getCapacity(),
                occupied, Math.max(0, r.getCapacity() - occupied), r.isActive(), occupants);
    }
}
