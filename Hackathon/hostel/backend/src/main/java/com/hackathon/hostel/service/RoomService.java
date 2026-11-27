package com.hackathon.hostel.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.hostel.dto.RoomRequestBody;
import com.hackathon.hostel.dto.RoomResponse;
import com.hackathon.hostel.entity.Room;
import com.hackathon.hostel.entity.RoomType;
import com.hackathon.hostel.exception.FieldValidationException;
import com.hackathon.hostel.exception.ResourceNotFoundException;
import com.hackathon.hostel.repo.RoomAllocationRepository;
import com.hackathon.hostel.repo.RoomRepository;

@Service
public class RoomService {

    private final RoomRepository rooms;
    private final RoomAllocationRepository allocations;

    public RoomService(RoomRepository rooms, RoomAllocationRepository allocations) {
        this.rooms = rooms;
        this.allocations = allocations;
    }

    /** Rooms that are active and still have a free bed, optionally of one type. For students. */
    @Transactional(readOnly = true)
    public List<RoomResponse> available(String type) {
        RoomType wanted = type == null || type.isBlank() ? null : parseType(type);
        Map<Long, List<String>> occupants = occupants();
        return rooms.findAllByOrderByFloorAscNumberAsc().stream()
                .filter(Room::isActive)
                .filter(r -> wanted == null || r.getType() == wanted)
                .map(r -> RoomResponse.from(r, occupants.getOrDefault(r.getId(), List.of()).size(), List.of())) // students only see how many beds are taken
                .filter(r -> r.bedsLeft() > 0)
                .collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('HOSTEL_ADMIN')")
    @Transactional(readOnly = true)
    public List<RoomResponse> listAll(String type) {
        RoomType wanted = type == null || type.isBlank() ? null : parseType(type);
        Map<Long, List<String>> occupants = occupants();
        return rooms.findAllByOrderByFloorAscNumberAsc().stream()
                .filter(r -> wanted == null || r.getType() == wanted)
                .map(r -> RoomResponse.from(r, occupants.getOrDefault(r.getId(), List.of())))
                .collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('HOSTEL_ADMIN')")
    @Transactional
    public RoomResponse create(RoomRequestBody body) {
        String number = normalise(body.number());
        if (rooms.existsByNumber(number)) {
            throw duplicate(number);
        }
        try {
            Room saved = rooms.saveAndFlush(new Room(number, body.floor(), parseType(body.type()), body.capacity()));
            return RoomResponse.from(saved, List.of());
        } catch (DataIntegrityViolationException ex) { // created at the same moment
            throw duplicate(number);
        }
    }

    @PreAuthorize("hasRole('HOSTEL_ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RoomResponse update(Long id, RoomRequestBody body) {
        Room room = rooms.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Room not found with id " + id));
        String number = normalise(body.number());
        if (!room.getNumber().equals(number) && rooms.existsByNumber(number)) {
            throw duplicate(number);
        }
        List<String> occupants = allocations.findByVacatedAtIsNull().stream()
                .filter(a -> a.getRoom().getId().equals(id)).map(a -> a.getStudent().getFullname()).collect(Collectors.toList());
        if (body.capacity() < occupants.size()) {
            throw new FieldValidationException("capacity", occupants.size() + " students live here, so the capacity cannot be below " + occupants.size());
        }
        room.update(number, body.floor(), parseType(body.type()), body.capacity(), body.active() == null || body.active());
        return RoomResponse.from(room, occupants);
    }

    /** Names of the students living in each room, by room id. */
    Map<Long, List<String>> occupants() {
        return allocations.findByVacatedAtIsNull().stream().collect(Collectors.groupingBy(
                a -> a.getRoom().getId(), Collectors.mapping(a -> a.getStudent().getFullname(), Collectors.toList())));
    }

    static RoomType parseType(String value) {
        try {
            return RoomType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new FieldValidationException("type", "Choose one of: single, double, triple, dormitory");
        }
    }

    private String normalise(String number) {
        return number.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private FieldValidationException duplicate(String number) {
        return new FieldValidationException("number", "Room " + number + " already exists");
    }

    /** Used by HostelService to check a room's free beds. */
    long occupiedBeds(Room room) {
        return allocations.countByRoomAndVacatedAtIsNull(room);
    }
}
