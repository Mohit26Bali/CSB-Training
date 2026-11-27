package com.hackathon.asset.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.asset.dto.AcceptBody;
import com.hackathon.asset.dto.AssetBody;
import com.hackathon.asset.dto.AssetDashboard;
import com.hackathon.asset.dto.AssetResponse;
import com.hackathon.asset.dto.AssignBody;
import com.hackathon.asset.dto.AssignmentResponse;
import com.hackathon.asset.dto.Count;
import com.hackathon.asset.dto.EmployeeResponse;
import com.hackathon.asset.entity.Asset;
import com.hackathon.asset.entity.AssetCategory;
import com.hackathon.asset.entity.AssetStatus;
import com.hackathon.asset.entity.Assignment;
import com.hackathon.asset.entity.AssignmentStatus;
import com.hackathon.asset.entity.Role;
import com.hackathon.asset.entity.User;
import com.hackathon.asset.exception.BusinessRuleException;
import com.hackathon.asset.exception.FieldValidationException;
import com.hackathon.asset.exception.ForbiddenActionException;
import com.hackathon.asset.exception.InvalidStatusException;
import com.hackathon.asset.exception.ResourceNotFoundException;
import com.hackathon.asset.repo.AssetRepository;
import com.hackathon.asset.repo.AssignmentRepository;
import com.hackathon.asset.repo.UserRepository;

/**
 * Assets, who holds them and the way they come back. Every change to an asset first takes that asset's row lock (under READ_COMMITTED, and
 * before the assignment is loaded), so an asset can never be handed to two people, and a return request and its acceptance cannot cross.
 */
@Service
public class AssetService {

    static final int MAX_ASSETS_PER_EMPLOYEE = 5;
    private static final Set<AssignmentStatus> ACTIVE = EnumSet.of(AssignmentStatus.ASSIGNED, AssignmentStatus.RETURN_REQUESTED);
    private static final Map<AssetStatus, Set<AssetStatus>> MOVES = Map.of(
            AssetStatus.AVAILABLE, EnumSet.of(AssetStatus.IN_REPAIR, AssetStatus.RETIRED),
            AssetStatus.IN_REPAIR, EnumSet.of(AssetStatus.AVAILABLE, AssetStatus.RETIRED));

    private final AssetRepository assets;
    private final AssignmentRepository assignments;
    private final UserRepository users;

    public AssetService(AssetRepository assets, AssignmentRepository assignments, UserRepository users) {
        this.assets = assets;
        this.assignments = assignments;
        this.users = users;
    }

    // ---------- admin: the register ----------

    /** The register, filtered by status and category, and searched by tag, serial number, name, brand, model or holder. */
    @PreAuthorize("hasRole('ASSET_ADMIN')")
    @Transactional(readOnly = true)
    public List<AssetResponse> assets(AssetStatus status, AssetCategory category, String search) {
        Map<Long, Assignment> holders = holders();
        String q = squash(search);
        return assets.findAllByOrderByAssetTagAsc().stream()
                .filter(a -> status == null || a.getStatus() == status)
                .filter(a -> category == null || a.getCategory() == category)
                .filter(a -> q.isEmpty() || Stream.of(a.getAssetTag(), a.getSerialNumber(), a.getName(), a.getBrand(), a.getModel(),
                        holderName(holders.get(a.getId())), holderUsername(holders.get(a.getId()))).anyMatch(s -> squash(s).contains(q)))
                .map(a -> AssetResponse.of(a, holders.get(a.getId()))).toList();
    }

    @PreAuthorize("hasRole('ASSET_ADMIN')")
    @Transactional(readOnly = true)
    public AssetResponse asset(Long id) {
        Asset asset = assets.findById(id).orElseThrow(() -> assetNotFound(id));
        return AssetResponse.of(asset, holders().get(id));
    }

    @PreAuthorize("hasRole('ASSET_ADMIN')")
    @Transactional
    public AssetResponse createAsset(AssetBody body) {
        String tag = body.assetTag().trim().toUpperCase(Locale.ROOT);
        String serial = body.serialNumber().trim().toUpperCase(Locale.ROOT);
        requireUnique(null, tag, serial);
        validatePurchase(body);
        try {
            Asset saved = assets.saveAndFlush(new Asset(tag, serial, body.name().trim(), body.category(), clean(body.brand()), clean(body.model()),
                    body.purchaseDate(), body.purchasePrice(), body.condition(), clean(body.notes())));
            return AssetResponse.of(saved, null);
        } catch (DataIntegrityViolationException e) {
            throw new FieldValidationException("assetTag", "An asset with that tag or serial number already exists");
        }
    }

    @PreAuthorize("hasRole('ASSET_ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AssetResponse updateAsset(Long id, AssetBody body) {
        Asset asset = assets.findByIdForUpdate(id).orElseThrow(() -> assetNotFound(id));
        if (asset.getStatus() == AssetStatus.RETIRED) {
            throw new BusinessRuleException("A retired asset is read-only");
        }
        String tag = body.assetTag().trim().toUpperCase(Locale.ROOT);
        String serial = body.serialNumber().trim().toUpperCase(Locale.ROOT);
        requireUnique(id, tag, serial);
        validatePurchase(body);
        asset.update(tag, serial, body.name().trim(), body.category(), clean(body.brand()), clean(body.model()), body.purchaseDate(),
                body.purchasePrice(), body.condition(), clean(body.notes()));
        return AssetResponse.of(asset, holders().get(id));
    }

    /** AVAILABLE and IN_REPAIR can be swapped, either can be retired for good; held assets must come back first. */
    @PreAuthorize("hasRole('ASSET_ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AssetResponse changeStatus(Long id, AssetStatus to) {
        Asset asset = assets.findByIdForUpdate(id).orElseThrow(() -> assetNotFound(id));
        AssetStatus from = asset.getStatus();
        if (from == AssetStatus.ASSIGNED || from == AssetStatus.RETURN_REQUESTED) {
            throw new BusinessRuleException("This asset is with an employee. It must be returned before its status can change");
        }
        if (from == AssetStatus.RETIRED) {
            throw new InvalidStatusException("A retired asset cannot be used again");
        }
        if (!MOVES.getOrDefault(from, Set.of()).contains(to)) {
            throw new InvalidStatusException("An asset that is " + from + " cannot be moved to " + to);
        }
        asset.setStatus(to);
        return AssetResponse.of(asset, null);
    }

    private void requireUnique(Long id, String tag, String serial) {
        if (assets.findByAssetTag(tag).filter(a -> !a.getId().equals(id)).isPresent()) {
            throw new FieldValidationException("assetTag", "Asset tag " + tag + " is already in use");
        }
        if (assets.findBySerialNumber(serial).filter(a -> !a.getId().equals(id)).isPresent()) {
            throw new FieldValidationException("serialNumber", "Serial number " + serial + " is already registered");
        }
    }

    private static void validatePurchase(AssetBody body) {
        if (body.purchaseDate() != null && body.purchaseDate().isAfter(LocalDate.now())) {
            throw new FieldValidationException("purchaseDate", "Purchase date cannot be in the future");
        }
    }

    // ---------- admin: handing over and taking back ----------

    @PreAuthorize("hasRole('ASSET_ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AssignmentResponse assign(String admin, Long assetId, AssignBody body) {
        Asset asset = assets.findByIdForUpdate(assetId).orElseThrow(() -> assetNotFound(assetId));
        String username = body.employeeUsername().trim();
        User employee = users.findByUsernameForUpdate(username)
                .orElseThrow(() -> new FieldValidationException("employeeUsername", "There is no employee called " + username));
        if (employee.getRole() != Role.EMPLOYEE || !employee.isEnabled()) {
            throw new FieldValidationException("employeeUsername", "Assets can only be assigned to active employees");
        }
        LocalDate today = LocalDate.now();
        if (body.dueDate() != null && !body.dueDate().isAfter(today)) {
            throw new FieldValidationException("dueDate", "Due date must be after today");
        }
        if (asset.getStatus() != AssetStatus.AVAILABLE) {
            Assignment holder = holders().get(assetId);
            throw new InvalidStatusException("Only available assets can be assigned. " + asset.getAssetTag() + " is " + asset.getStatus()
                    + (holder == null ? "" : " to " + holder.getEmployee().getFullname()));
        }
        if (assignments.countByEmployeeAndStatusIn(employee, ACTIVE) >= MAX_ASSETS_PER_EMPLOYEE) {
            throw new BusinessRuleException(employee.getFullname() + " already holds " + MAX_ASSETS_PER_EMPLOYEE + " assets, the most one person can have");
        }
        Assignment saved = assignments.save(new Assignment(asset, employee, admin, body.dueDate(), clean(body.note())));
        asset.setStatus(AssetStatus.ASSIGNED);
        return AssignmentResponse.of(saved, today);
    }

    /** Assignments for the admin: the return queue by default, or every one that is still open. Oldest request first. */
    @PreAuthorize("hasRole('ASSET_ADMIN')")
    @Transactional(readOnly = true)
    public List<AssignmentResponse> openAssignments(AssignmentStatus status) {
        LocalDate today = LocalDate.now();
        Set<AssignmentStatus> wanted = status == null ? ACTIVE : EnumSet.of(status);
        return assignments.findByStatusInOrderByAssignedAtAsc(wanted).stream()
                .sorted(Comparator.comparing((Assignment a) -> a.getStatus() == AssignmentStatus.RETURN_REQUESTED ? 0 : 1)
                        .thenComparing(a -> a.getReturnRequestedAt() == null ? a.getAssignedAt() : a.getReturnRequestedAt()))
                .map(a -> AssignmentResponse.of(a, today)).toList();
    }

    @PreAuthorize("hasRole('ASSET_ADMIN')")
    @Transactional(readOnly = true)
    public List<AssignmentResponse> history(Long assetId) {
        Asset asset = assets.findById(assetId).orElseThrow(() -> assetNotFound(assetId));
        LocalDate today = LocalDate.now();
        return assignments.findByAssetOrderByAssignedAtDesc(asset).stream().map(a -> AssignmentResponse.of(a, today)).toList();
    }

    @PreAuthorize("hasRole('ASSET_ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AssignmentResponse accept(String admin, Long assignmentId, AcceptBody body) {
        AssignmentRepository.Peek peek = assignments.peek(assignmentId).orElseThrow(() -> notFound(assignmentId));
        Asset asset = assets.findByIdForUpdate(peek.getAssetId()).orElseThrow();
        Assignment assignment = detailed(assignmentId);
        if (assignment.getStatus() != AssignmentStatus.RETURN_REQUESTED) {
            throw new InvalidStatusException(assignment.getStatus() == AssignmentStatus.RETURNED
                    ? "This asset has already been returned"
                    : "The employee has not asked to return this asset yet");
        }
        assignment.accept(admin, body.condition(), clean(body.note()));
        asset.setCondition(body.condition());
        asset.setStatus(AssetStatus.AVAILABLE);
        return AssignmentResponse.of(assignment, LocalDate.now());
    }

    @PreAuthorize("hasRole('ASSET_ADMIN')")
    @Transactional(readOnly = true)
    public List<EmployeeResponse> employees(String search) {
        String q = squash(search);
        Map<Long, Long> held = assignments.findByStatusInOrderByAssignedAtAsc(ACTIVE).stream()
                .collect(Collectors.groupingBy(a -> a.getEmployee().getId(), Collectors.counting()));
        return users.findByRoleOrderByFullnameAsc(Role.EMPLOYEE).stream().filter(User::isEnabled)
                .filter(u -> q.isEmpty() || squash(u.getFullname()).contains(q) || squash(u.getUsername()).contains(q))
                .map(u -> new EmployeeResponse(u.getId(), u.getUsername(), u.getFullname(), held.getOrDefault(u.getId(), 0L).intValue())).toList();
    }

    @PreAuthorize("hasRole('ASSET_ADMIN')")
    @Transactional(readOnly = true)
    public AssetDashboard dashboard() {
        LocalDate today = LocalDate.now();
        List<Asset> all = assets.findAll();
        List<Assignment> open = assignments.findByStatusInOrderByAssignedAtAsc(ACTIVE);
        List<Asset> inUse = all.stream().filter(a -> a.getStatus() != AssetStatus.RETIRED).toList();
        Function<AssetStatus, Long> countOf = s -> all.stream().filter(a -> a.getStatus() == s).count();
        return new AssetDashboard(all.size(), countOf.apply(AssetStatus.AVAILABLE),
                countOf.apply(AssetStatus.ASSIGNED) + countOf.apply(AssetStatus.RETURN_REQUESTED), countOf.apply(AssetStatus.RETURN_REQUESTED),
                countOf.apply(AssetStatus.IN_REPAIR), countOf.apply(AssetStatus.RETIRED),
                open.stream().filter(a -> a.getDueDate() != null && a.getDueDate().isBefore(today)).count(),
                inUse.stream().filter(a -> a.getCondition() == com.hackathon.asset.entity.AssetCondition.POOR).count(),
                inUse.stream().map(Asset::getPurchasePrice).filter(p -> p != null).reduce(BigDecimal.ZERO, BigDecimal::add),
                inUse.stream().collect(Collectors.groupingBy(a -> a.getCategory().name(), Collectors.counting())).entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                        .map(e -> new Count(e.getKey(), e.getValue())).toList(),
                all.stream().collect(Collectors.groupingBy(a -> a.getStatus().name(), Collectors.counting())).entrySet().stream()
                        .sorted(Map.Entry.comparingByKey()).map(e -> new Count(e.getKey(), e.getValue())).toList(),
                open.stream().filter(a -> a.getStatus() == AssignmentStatus.RETURN_REQUESTED)
                        .sorted(Comparator.comparing(Assignment::getReturnRequestedAt)).limit(5).map(a -> AssignmentResponse.of(a, today)).toList());
    }

    // ---------- employees ----------

    /** Assets the employee holds now, including those they have asked to return. */
    @PreAuthorize("hasRole('EMPLOYEE')")
    @Transactional(readOnly = true)
    public List<AssignmentResponse> myAssets(String username) {
        LocalDate today = LocalDate.now();
        return assignments.findByEmployeeOrderByAssignedAtDesc(user(username)).stream().filter(Assignment::isActive)
                .map(a -> AssignmentResponse.of(a, today)).toList();
    }

    /** Assets the employee has handed back, newest first. */
    @PreAuthorize("hasRole('EMPLOYEE')")
    @Transactional(readOnly = true)
    public List<AssignmentResponse> myHistory(String username) {
        LocalDate today = LocalDate.now();
        return assignments.findByEmployeeOrderByAssignedAtDesc(user(username)).stream().filter(a -> !a.isActive())
                .map(a -> AssignmentResponse.of(a, today)).toList();
    }

    @PreAuthorize("hasRole('EMPLOYEE')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AssignmentResponse requestReturn(String username, Long assignmentId, String note) {
        AssignmentRepository.Peek peek = assignments.peek(assignmentId).orElseThrow(() -> notFound(assignmentId));
        if (!peek.getEmployeeUsername().equals(username)) {
            throw new ForbiddenActionException("You can only return assets that are assigned to you");
        }
        Asset asset = assets.findByIdForUpdate(peek.getAssetId()).orElseThrow();
        Assignment assignment = detailed(assignmentId);
        if (assignment.getStatus() != AssignmentStatus.ASSIGNED) {
            throw new InvalidStatusException(assignment.getStatus() == AssignmentStatus.RETURNED
                    ? "You have already returned this asset"
                    : "A return has already been requested for this asset");
        }
        assignment.requestReturn(clean(note));
        asset.setStatus(AssetStatus.RETURN_REQUESTED);
        return AssignmentResponse.of(assignment, LocalDate.now());
    }

    // ---------- helpers ----------

    private Map<Long, Assignment> holders() {
        return assignments.findByStatusInOrderByAssignedAtAsc(ACTIVE).stream()
                .collect(Collectors.toMap(a -> a.getAsset().getId(), a -> a, (a, b) -> b));
    }

    private static String holderName(Assignment a) {
        return a == null ? null : a.getEmployee().getFullname();
    }

    private static String holderUsername(Assignment a) {
        return a == null ? null : a.getEmployee().getUsername();
    }

    private User user(String username) {
        return users.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("User " + username + " not found"));
    }

    private Assignment detailed(Long id) {
        return assignments.findDetailedById(id).orElseThrow(() -> notFound(id));
    }

    private static ResourceNotFoundException assetNotFound(Long id) {
        return new ResourceNotFoundException("Asset " + id + " not found");
    }

    private static ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException("Assignment " + id + " not found");
    }

    /** Lower-case without spaces and dashes, so "lt 1001" finds "LT-1001". */
    private static String squash(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replace(" ", "").replace("-", "");
    }

    private static String clean(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
