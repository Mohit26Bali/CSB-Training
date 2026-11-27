package com.hackathon.parking.service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.parking.dto.AllocationResponse;
import com.hackathon.parking.dto.AskBody;
import com.hackathon.parking.dto.FloorStats;
import com.hackathon.parking.dto.ParkingDashboard;
import com.hackathon.parking.dto.RequestResponse;
import com.hackathon.parking.dto.SlotBody;
import com.hackathon.parking.dto.SlotResponse;
import com.hackathon.parking.entity.ParkingAllocation;
import com.hackathon.parking.entity.ParkingRequest;
import com.hackathon.parking.entity.ParkingSlot;
import com.hackathon.parking.entity.RequestStatus;
import com.hackathon.parking.entity.SlotStatus;
import com.hackathon.parking.entity.User;
import com.hackathon.parking.exception.BusinessRuleException;
import com.hackathon.parking.exception.ConflictException;
import com.hackathon.parking.exception.FieldValidationException;
import com.hackathon.parking.exception.ForbiddenActionException;
import com.hackathon.parking.exception.InvalidStatusException;
import com.hackathon.parking.exception.ResourceNotFoundException;
import com.hackathon.parking.repo.ParkingAllocationRepository;
import com.hackathon.parking.repo.ParkingRequestRepository;
import com.hackathon.parking.repo.SlotRepository;
import com.hackathon.parking.repo.UserRepository;

/**
 * Slots, requests and allocations. Approving locks the slot row, then the employee row, and only then loads the request,
 * so a slot goes to one person, an employee holds one slot, and a decided request cannot be decided twice, however many
 * admins click at the same moment.
 */
@Service
public class ParkingService {

    private final SlotRepository slots;
    private final ParkingRequestRepository requests;
    private final ParkingAllocationRepository allocations;
    private final UserRepository users;

    public ParkingService(SlotRepository slots, ParkingRequestRepository requests, ParkingAllocationRepository allocations,
                          UserRepository users) {
        this.slots = slots;
        this.requests = requests;
        this.allocations = allocations;
        this.users = users;
    }

    // ---------- slots ----------

    /** Slots marked AVAILABLE that nobody holds, optionally on one floor. For employees (no names). */
    @Transactional(readOnly = true)
    public List<SlotResponse> freeSlots(Integer floor) {
        Map<Long, ParkingAllocation> taken = activeBySlot();
        return slots.findAllByOrderByFloorAscCodeAsc().stream()
                .filter(s -> s.getStatus() == SlotStatus.AVAILABLE && !taken.containsKey(s.getId()))
                .filter(s -> floor == null || s.getFloor() == floor)
                .map(s -> SlotResponse.from(s, false, null, null)).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Integer> floors() {
        return slots.findAllByOrderByFloorAscCodeAsc().stream().map(ParkingSlot::getFloor).distinct().sorted().collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('PARKING_ADMIN')")
    @Transactional(readOnly = true)
    public List<SlotResponse> allSlots(Integer floor) {
        Map<Long, ParkingAllocation> taken = activeBySlot();
        return slots.findAllByOrderByFloorAscCodeAsc().stream()
                .filter(s -> floor == null || s.getFloor() == floor)
                .map(s -> {
                    ParkingAllocation a = taken.get(s.getId());
                    return SlotResponse.from(s, a != null, a == null ? null : a.getEmployee().getFullname(), a == null ? null : a.getVehicleNumber());
                }).collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('PARKING_ADMIN')")
    @Transactional
    public SlotResponse createSlot(SlotBody body) {
        String code = normaliseCode(body.code());
        if (slots.existsByCode(code)) {
            throw duplicate(code);
        }
        SlotStatus status = body.status() == null || body.status().isBlank() ? SlotStatus.AVAILABLE : parseStatus(body.status());
        try {
            return SlotResponse.from(slots.saveAndFlush(new ParkingSlot(code, body.floor(), status)), false, null, null);
        } catch (DataIntegrityViolationException ex) { // created at the same moment
            throw duplicate(code);
        }
    }

    @PreAuthorize("hasRole('PARKING_ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SlotResponse updateSlot(Long id, SlotBody body) {
        ParkingSlot slot = slots.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Slot not found with id " + id));
        String code = normaliseCode(body.code());
        if (!slot.getCode().equals(code) && slots.existsByCode(code)) {
            throw duplicate(code);
        }
        SlotStatus status = body.status() == null || body.status().isBlank() ? slot.getStatus() : parseStatus(body.status());
        ParkingAllocation holder = activeBySlot().get(id);
        if (holder != null && status != SlotStatus.AVAILABLE) {
            throw new BusinessRuleException("Slot " + slot.getCode() + " is in use by " + holder.getEmployee().getFullname() + ". Release it first.");
        }
        slot.update(code, body.floor(), status);
        return SlotResponse.from(slot, holder != null, holder == null ? null : holder.getEmployee().getFullname(),
                holder == null ? null : holder.getVehicleNumber());
    }

    // ---------- employee ----------

    @PreAuthorize("hasRole('EMPLOYEE')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RequestResponse ask(String username, AskBody body) {
        User employee = users.findByUsernameForUpdate(username).orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        String vehicle = normaliseVehicle(body.vehicleNumber());
        if (allocations.existsByEmployeeAndReleasedAtIsNull(employee)) {
            throw new ConflictException("You already have a parking slot. Ask the admin to release it before requesting another.");
        }
        if (requests.existsByEmployeeAndStatus(employee, RequestStatus.PENDING)) {
            throw new ConflictException("You already have a pending request. Cancel it first to send a new one.");
        }
        if (allocations.existsByVehicleNumberAndReleasedAtIsNull(vehicle)) {
            throw new ConflictException("Vehicle " + vehicle + " is already parked in an allocated slot");
        }
        String note = body.note() == null || body.note().isBlank() ? null : body.note().trim();
        ParkingRequest saved = requests.save(new ParkingRequest(employee, vehicle, body.preferredFloor(), note));
        return RequestResponse.from(saved, queuePositions().get(saved.getId()));
    }

    @PreAuthorize("hasRole('EMPLOYEE')")
    @Transactional(readOnly = true)
    public List<RequestResponse> myRequests(String username) {
        User employee = findUser(username);
        Map<Long, Integer> positions = queuePositions();
        return requests.findByEmployeeOrderByCreatedAtDesc(employee).stream()
                .map(r -> RequestResponse.from(r, positions.get(r.getId()))).collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('EMPLOYEE')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RequestResponse cancel(String username, Long id) {
        ParkingRequestRepository.Peek peek = requests.peek(id).orElseThrow(() -> new ResourceNotFoundException("Request not found with id " + id));
        if (!peek.getEmployeeUsername().equals(username)) {
            throw new ForbiddenActionException("You can only cancel your own request");
        }
        users.findByUsernameForUpdate(username); // serialise with an admin approving it right now
        ParkingRequest request = requests.findById(id).orElseThrow();
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new InvalidStatusException("Only a pending request can be cancelled; this one is " + request.getStatus().name().toLowerCase(Locale.ROOT));
        }
        request.cancel();
        return RequestResponse.from(request, null);
    }

    @PreAuthorize("hasRole('EMPLOYEE')")
    @Transactional(readOnly = true)
    public AllocationResponse myAllocation(String username) {
        return allocations.findByEmployeeAndReleasedAtIsNull(findUser(username)).map(AllocationResponse::from).orElse(null);
    }

    // ---------- admin ----------

    /** Pending requests come oldest first: that is the queue. */
    @PreAuthorize("hasRole('PARKING_ADMIN')")
    @Transactional(readOnly = true)
    public List<RequestResponse> allRequests(RequestStatus status) {
        Map<Long, Integer> positions = queuePositions();
        return requests.findAllByOrderByCreatedAtAsc().stream().filter(r -> status == null || r.getStatus() == status)
                .map(r -> RequestResponse.from(r, positions.get(r.getId()))).collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('PARKING_ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RequestResponse approve(Long id, Long slotId, String admin) {
        ParkingRequestRepository.Peek peek = requests.peek(id).orElseThrow(() -> new ResourceNotFoundException("Request not found with id " + id));
        ParkingSlot slot = slots.findByIdForUpdate(slotId).orElseThrow(() -> new FieldValidationException("slotId", "That slot does not exist"));
        User employee = users.findByUsernameForUpdate(peek.getEmployeeUsername()).orElseThrow();
        ParkingRequest request = requests.findById(id).orElseThrow(); // loaded only after both locks are held
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new InvalidStatusException("This request is already " + request.getStatus().name().toLowerCase(Locale.ROOT));
        }
        if (slot.getStatus() != SlotStatus.AVAILABLE) {
            throw new BusinessRuleException("Slot " + slot.getCode() + " is " + slot.getStatus().name().toLowerCase(Locale.ROOT) + " and cannot be allocated");
        }
        if (allocations.existsByEmployeeAndReleasedAtIsNull(employee)) {
            throw new ConflictException(employee.getFullname() + " already has a slot");
        }
        if (allocations.existsBySlotAndReleasedAtIsNull(slot)) {
            throw new ConflictException("Slot " + slot.getCode() + " is already taken");
        }
        if (allocations.existsByVehicleNumberAndReleasedAtIsNull(request.getVehicleNumber())) {
            throw new ConflictException("Vehicle " + request.getVehicleNumber() + " is already parked in another slot");
        }
        allocations.save(new ParkingAllocation(slot, employee, request.getVehicleNumber(), admin));
        request.approve(admin, slot);
        return RequestResponse.from(request, null);
    }

    @PreAuthorize("hasRole('PARKING_ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RequestResponse reject(Long id, String reason, String admin) {
        ParkingRequestRepository.Peek peek = requests.peek(id).orElseThrow(() -> new ResourceNotFoundException("Request not found with id " + id));
        users.findByUsernameForUpdate(peek.getEmployeeUsername());
        ParkingRequest request = requests.findById(id).orElseThrow();
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new InvalidStatusException("This request is already " + request.getStatus().name().toLowerCase(Locale.ROOT));
        }
        request.reject(admin, reason.trim());
        return RequestResponse.from(request, null);
    }

    @PreAuthorize("hasRole('PARKING_ADMIN')")
    @Transactional(readOnly = true)
    public List<AllocationResponse> allocations(boolean activeOnly) {
        return allocations.findAllByOrderByAllocatedAtDesc().stream().filter(a -> !activeOnly || a.isActive())
                .map(AllocationResponse::from).collect(Collectors.toList());
    }

    /** Ends the allocation; the slot is free for the next request. */
    @PreAuthorize("hasRole('PARKING_ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AllocationResponse release(Long id, String admin) {
        Long slotId = allocations.slotIdOf(id).orElseThrow(() -> new ResourceNotFoundException("Allocation not found with id " + id));
        slots.findByIdForUpdate(slotId);
        ParkingAllocation allocation = allocations.findById(id).orElseThrow();
        if (!allocation.isActive()) {
            throw new InvalidStatusException("This slot has already been released");
        }
        allocation.release(admin);
        return AllocationResponse.from(allocation);
    }

    @PreAuthorize("hasRole('PARKING_ADMIN')")
    @Transactional(readOnly = true)
    public ParkingDashboard dashboard() {
        List<ParkingSlot> all = slots.findAllByOrderByFloorAscCodeAsc();
        Map<Long, ParkingAllocation> taken = activeBySlot();
        long occupied = all.stream().filter(s -> taken.containsKey(s.getId())).count();
        long reserved = all.stream().filter(s -> s.getStatus() == SlotStatus.RESERVED).count();
        long maintenance = all.stream().filter(s -> s.getStatus() == SlotStatus.MAINTENANCE).count();
        long usable = all.size() - reserved - maintenance;
        Map<Integer, List<ParkingSlot>> byFloor = all.stream().collect(Collectors.groupingBy(ParkingSlot::getFloor));
        List<FloorStats> floors = byFloor.keySet().stream().sorted().map(f -> {
            List<ParkingSlot> list = byFloor.get(f);
            int occ = (int) list.stream().filter(s -> taken.containsKey(s.getId())).count();
            int free = (int) list.stream().filter(s -> s.getStatus() == SlotStatus.AVAILABLE && !taken.containsKey(s.getId())).count();
            return new FloorStats(f, list.size(), occ, free);
        }).collect(Collectors.toList());
        long pending = requests.findAllByOrderByCreatedAtAsc().stream().filter(r -> r.getStatus() == RequestStatus.PENDING).count();
        return new ParkingDashboard(all.size(), (int) occupied, (int) Math.max(0, usable - occupied), (int) reserved, (int) maintenance,
                usable == 0 ? 0 : (int) Math.round(100.0 * occupied / usable), pending, floors);
    }

    // ---------- helpers ----------

    private User findUser(String username) {
        return users.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }

    private Map<Long, ParkingAllocation> activeBySlot() {
        return allocations.findByReleasedAtIsNull().stream().collect(Collectors.toMap(a -> a.getSlot().getId(), Function.identity()));
    }

    /** Request id -> 1-based place in the queue, for pending requests only. */
    private Map<Long, Integer> queuePositions() {
        List<Long> pending = requests.findAllByOrderByCreatedAtAsc().stream()
                .filter(r -> r.getStatus() == RequestStatus.PENDING).map(ParkingRequest::getId).collect(Collectors.toList());
        return pending.stream().collect(Collectors.toMap(Function.identity(), id -> pending.indexOf(id) + 1));
    }

    private String normaliseCode(String code) {
        return code.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    /** "mh 12-ab 1234" -> "MH12AB1234". Must have 6 to 12 letters or digits, at least one of each. */
    static String normaliseVehicle(String raw) {
        String vehicle = raw.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (!vehicle.matches("[A-Z0-9]{6,12}") || !vehicle.matches(".*[A-Z].*") || !vehicle.matches(".*[0-9].*")) {
            throw new FieldValidationException("vehicleNumber", "Enter a valid vehicle number such as MH12AB1234");
        }
        return vehicle;
    }

    private SlotStatus parseStatus(String value) {
        try {
            return SlotStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new FieldValidationException("status", "Choose one of: " + Arrays.stream(SlotStatus.values()).map(s -> s.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining(", ")));
        }
    }

    private FieldValidationException duplicate(String code) {
        return new FieldValidationException("code", "Slot " + code + " already exists");
    }
}
