package com.hackathon.hostel.service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.hostel.dto.AllocationResponse;
import com.hackathon.hostel.dto.HostelDashboard;
import com.hackathon.hostel.dto.RequestResponse;
import com.hackathon.hostel.dto.RoomAskBody;
import com.hackathon.hostel.dto.TypeOccupancy;
import com.hackathon.hostel.entity.RequestStatus;
import com.hackathon.hostel.entity.Room;
import com.hackathon.hostel.entity.RoomAllocation;
import com.hackathon.hostel.entity.RoomRequest;
import com.hackathon.hostel.entity.RoomType;
import com.hackathon.hostel.entity.User;
import com.hackathon.hostel.exception.BusinessRuleException;
import com.hackathon.hostel.exception.ConflictException;
import com.hackathon.hostel.exception.FieldValidationException;
import com.hackathon.hostel.exception.ForbiddenActionException;
import com.hackathon.hostel.exception.InvalidStatusException;
import com.hackathon.hostel.exception.ResourceNotFoundException;
import com.hackathon.hostel.repo.RoomAllocationRepository;
import com.hackathon.hostel.repo.RoomRepository;
import com.hackathon.hostel.repo.RoomRequestRepository;
import com.hackathon.hostel.repo.UserRepository;

/**
 * Room requests and allocations. Approving locks the room row, then the student row, then loads the request, so a bed
 * can only be given once and a student can only end up in one room, however many admins click at the same time.
 */
@Service
public class HostelService {

    private final RoomRequestRepository requests;
    private final RoomAllocationRepository allocations;
    private final RoomRepository rooms;
    private final UserRepository users;
    private final RoomService roomService;

    public HostelService(RoomRequestRepository requests, RoomAllocationRepository allocations, RoomRepository rooms,
                         UserRepository users, RoomService roomService) {
        this.requests = requests;
        this.allocations = allocations;
        this.rooms = rooms;
        this.users = users;
        this.roomService = roomService;
    }

    // ---------- student ----------

    @PreAuthorize("hasRole('STUDENT')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RequestResponse ask(String username, RoomAskBody body) {
        User student = users.findByUsernameForUpdate(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        RoomType preferred = body.preferredType() == null || body.preferredType().isBlank() ? null : RoomService.parseType(body.preferredType());
        if (allocations.existsByStudentAndVacatedAtIsNull(student)) {
            throw new ConflictException("You already have a room. Ask the warden if you want to move.");
        }
        if (requests.existsByStudentAndStatus(student, RequestStatus.PENDING)) {
            throw new ConflictException("You already have a pending request. Cancel it first to send a new one.");
        }
        String note = body.note() == null || body.note().isBlank() ? null : body.note().trim();
        RoomRequest saved = requests.save(new RoomRequest(student, preferred, note));
        return RequestResponse.from(saved, queuePositions().get(saved.getId()));
    }

    @PreAuthorize("hasRole('STUDENT')")
    @Transactional(readOnly = true)
    public List<RequestResponse> myRequests(String username) {
        User student = findStudent(username);
        Map<Long, Integer> positions = queuePositions();
        return requests.findByStudentOrderByCreatedAtDesc(student).stream()
                .map(r -> RequestResponse.from(r, positions.get(r.getId()))).collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('STUDENT')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RequestResponse cancel(String username, Long id) {
        RoomRequestRepository.Peek peek = requests.peek(id)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found with id " + id));
        if (!peek.getStudentUsername().equals(username)) {
            throw new ForbiddenActionException("You can only cancel your own request");
        }
        users.findByUsernameForUpdate(username); // serialise with an admin approving it right now
        RoomRequest request = requests.findById(id).orElseThrow();
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new InvalidStatusException("Only a pending request can be cancelled; this one is "
                    + request.getStatus().name().toLowerCase(Locale.ROOT));
        }
        request.cancel();
        return RequestResponse.from(request, null);
    }

    @PreAuthorize("hasRole('STUDENT')")
    @Transactional(readOnly = true)
    public AllocationResponse myAllocation(String username) {
        return allocations.findByStudentAndVacatedAtIsNull(findStudent(username)).map(AllocationResponse::from).orElse(null);
    }

    // ---------- admin ----------

    /** Pending requests come oldest first: that is the waiting list. */
    @PreAuthorize("hasRole('HOSTEL_ADMIN')")
    @Transactional(readOnly = true)
    public List<RequestResponse> allRequests(RequestStatus status) {
        Map<Long, Integer> positions = queuePositions();
        return requests.findAllByOrderByCreatedAtAsc().stream()
                .filter(r -> status == null || r.getStatus() == status)
                .map(r -> RequestResponse.from(r, positions.get(r.getId()))).collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('HOSTEL_ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RequestResponse approve(Long id, Long roomId, String admin) {
        RoomRequestRepository.Peek peek = requests.peek(id)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found with id " + id));
        Room room = rooms.findByIdForUpdate(roomId).orElseThrow(() -> new FieldValidationException("roomId", "That room does not exist"));
        User student = users.findByUsernameForUpdate(peek.getStudentUsername()).orElseThrow();
        RoomRequest request = requests.findById(id).orElseThrow(); // loaded only after both locks are held
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new InvalidStatusException("This request is already " + request.getStatus().name().toLowerCase(Locale.ROOT));
        }
        if (!room.isActive()) {
            throw new BusinessRuleException("Room " + room.getNumber() + " is closed for maintenance");
        }
        if (allocations.existsByStudentAndVacatedAtIsNull(student)) {
            throw new ConflictException(student.getFullname() + " already has a room");
        }
        if (roomService.occupiedBeds(room) >= room.getCapacity()) {
            throw new ConflictException("Room " + room.getNumber() + " is full");
        }
        allocations.save(new RoomAllocation(room, student, admin));
        request.approve(admin, room);
        return RequestResponse.from(request, null);
    }

    @PreAuthorize("hasRole('HOSTEL_ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RequestResponse reject(Long id, String reason, String admin) {
        RoomRequestRepository.Peek peek = requests.peek(id)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found with id " + id));
        users.findByUsernameForUpdate(peek.getStudentUsername());
        RoomRequest request = requests.findById(id).orElseThrow();
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new InvalidStatusException("This request is already " + request.getStatus().name().toLowerCase(Locale.ROOT));
        }
        request.reject(admin, reason.trim());
        return RequestResponse.from(request, null);
    }

    @PreAuthorize("hasRole('HOSTEL_ADMIN')")
    @Transactional(readOnly = true)
    public List<AllocationResponse> allocations(boolean activeOnly) {
        return allocations.findAllByOrderByAllocatedAtDesc().stream()
                .filter(a -> !activeOnly || a.isActive()).map(AllocationResponse::from).collect(Collectors.toList());
    }

    /** The student leaves; the bed becomes free for the next request. */
    @PreAuthorize("hasRole('HOSTEL_ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AllocationResponse vacate(Long id) {
        Long roomId = allocations.roomIdOf(id)
                .orElseThrow(() -> new ResourceNotFoundException("Allocation not found with id " + id));
        rooms.findByIdForUpdate(roomId);
        RoomAllocation fresh = allocations.findById(id).orElseThrow();
        if (!fresh.isActive()) {
            throw new InvalidStatusException("This student has already left the room");
        }
        fresh.vacate();
        return AllocationResponse.from(fresh);
    }

    @PreAuthorize("hasRole('HOSTEL_ADMIN')")
    @Transactional(readOnly = true)
    public HostelDashboard dashboard() {
        List<Room> all = rooms.findAllByOrderByFloorAscNumberAsc();
        Map<Long, Long> occupiedByRoom = allocations.findByVacatedAtIsNull().stream()
                .collect(Collectors.groupingBy(a -> a.getRoom().getId(), Collectors.counting()));
        List<Room> usable = all.stream().filter(Room::isActive).collect(Collectors.toList());
        int beds = usable.stream().mapToInt(Room::getCapacity).sum();
        int occupied = (int) occupiedByRoom.values().stream().mapToLong(Long::longValue).sum();
        int fullRooms = (int) usable.stream().filter(r -> occupiedByRoom.getOrDefault(r.getId(), 0L) >= r.getCapacity()).count();
        Map<RoomType, List<Room>> byType = all.stream().collect(Collectors.groupingBy(Room::getType));
        List<TypeOccupancy> types = Arrays.stream(RoomType.values()).filter(byType::containsKey).map(t -> {
            List<Room> list = byType.get(t);
            return new TypeOccupancy(t.name(), list.size(), list.stream().mapToInt(Room::getCapacity).sum(),
                    list.stream().mapToInt(r -> occupiedByRoom.getOrDefault(r.getId(), 0L).intValue()).sum());
        }).collect(Collectors.toList());
        long pending = requests.findAllByOrderByCreatedAtAsc().stream().filter(r -> r.getStatus() == RequestStatus.PENDING).count();
        return new HostelDashboard(all.size(), beds, occupied, Math.max(0, beds - occupied),
                beds == 0 ? 0 : (int) Math.round(100.0 * occupied / beds), pending, fullRooms, types);
    }

    // ---------- helpers ----------

    private User findStudent(String username) {
        return users.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }

    /** Request id -> 1-based place on the waiting list, for pending requests only. */
    private Map<Long, Integer> queuePositions() {
        List<Long> pending = requests.findAllByOrderByCreatedAtAsc().stream()
                .filter(r -> r.getStatus() == RequestStatus.PENDING).map(RoomRequest::getId).collect(Collectors.toList());
        return pending.stream().collect(Collectors.toMap(Function.identity(), id -> pending.indexOf(id) + 1));
    }
}
