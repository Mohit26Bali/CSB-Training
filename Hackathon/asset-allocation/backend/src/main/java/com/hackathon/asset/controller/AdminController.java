package com.hackathon.asset.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hackathon.asset.dto.AcceptBody;
import com.hackathon.asset.dto.AssetBody;
import com.hackathon.asset.dto.AssetDashboard;
import com.hackathon.asset.dto.AssetResponse;
import com.hackathon.asset.dto.AssignBody;
import com.hackathon.asset.dto.AssignmentResponse;
import com.hackathon.asset.dto.EmployeeResponse;
import com.hackathon.asset.dto.StatusBody;
import com.hackathon.asset.entity.AssetCategory;
import com.hackathon.asset.entity.AssetStatus;
import com.hackathon.asset.entity.AssignmentStatus;
import com.hackathon.asset.service.AssetService;

import jakarta.validation.Valid;

/** Asset admin endpoints: the register, handing assets over, accepting returns, the history and the dashboard. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AssetService service;

    public AdminController(AssetService service) {
        this.service = service;
    }

    @GetMapping("/assets")
    public List<AssetResponse> assets(@RequestParam(required = false) AssetStatus status, @RequestParam(required = false) AssetCategory category,
                                      @RequestParam(required = false) String search) {
        return service.assets(status, category, search);
    }

    @GetMapping("/assets/{id}")
    public AssetResponse asset(@PathVariable Long id) {
        return service.asset(id);
    }

    @PostMapping("/assets")
    public ResponseEntity<AssetResponse> create(@Valid @RequestBody AssetBody body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createAsset(body));
    }

    @PutMapping("/assets/{id}")
    public AssetResponse update(@PathVariable Long id, @Valid @RequestBody AssetBody body) {
        return service.updateAsset(id, body);
    }

    @PutMapping("/assets/{id}/status")
    public AssetResponse status(@PathVariable Long id, @Valid @RequestBody StatusBody body) {
        return service.changeStatus(id, body.status());
    }

    @PostMapping("/assets/{id}/assign")
    public ResponseEntity<AssignmentResponse> assign(@PathVariable Long id, @Valid @RequestBody AssignBody body, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.assign(authentication.getName(), id, body));
    }

    @GetMapping("/assets/{id}/history")
    public List<AssignmentResponse> history(@PathVariable Long id) {
        return service.history(id);
    }

    @GetMapping("/assignments")
    public List<AssignmentResponse> assignments(@RequestParam(required = false) AssignmentStatus status) {
        return service.openAssignments(status);
    }

    @PutMapping("/assignments/{id}/accept")
    public AssignmentResponse accept(@PathVariable Long id, @Valid @RequestBody AcceptBody body, Authentication authentication) {
        return service.accept(authentication.getName(), id, body);
    }

    @GetMapping("/employees")
    public List<EmployeeResponse> employees(@RequestParam(required = false) String search) {
        return service.employees(search);
    }

    @GetMapping("/dashboard")
    public AssetDashboard dashboard() {
        return service.dashboard();
    }
}
