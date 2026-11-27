package com.hackathon.maintenance.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hackathon.maintenance.dto.AssignBody;
import com.hackathon.maintenance.dto.Dashboard;
import com.hackathon.maintenance.dto.RequestResponse;
import com.hackathon.maintenance.dto.StatusBody;
import com.hackathon.maintenance.dto.TechnicianBody;
import com.hackathon.maintenance.dto.TechnicianResponse;
import com.hackathon.maintenance.entity.Category;
import com.hackathon.maintenance.entity.Priority;
import com.hackathon.maintenance.entity.RequestStatus;
import com.hackathon.maintenance.service.MaintenanceService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/manager")
public class ManagerController {

    private final MaintenanceService service;

    public ManagerController(MaintenanceService service) {
        this.service = service;
    }

    @GetMapping("/requests")
    public List<RequestResponse> queue(@RequestParam(required = false) RequestStatus status, @RequestParam(required = false) Priority priority,
                                       @RequestParam(required = false) Category category, @RequestParam(required = false) Long technicianId,
                                       @RequestParam(required = false) String aging, @RequestParam(required = false) String search) {
        return service.queue(status, priority, category, technicianId, aging, search);
    }

    @PutMapping("/requests/{id}/assign")
    public RequestResponse assign(@PathVariable Long id, @Valid @RequestBody AssignBody body) {
        return service.assign(id, body.technicianId());
    }

    @PutMapping("/requests/{id}/status")
    public RequestResponse status(@PathVariable Long id, @Valid @RequestBody StatusBody body) {
        return service.updateStatus(id, body.status(), body.notes());
    }

    @GetMapping("/technicians")
    public List<TechnicianResponse> technicians() {
        return service.technicians();
    }

    @PostMapping("/technicians")
    public ResponseEntity<TechnicianResponse> addTechnician(@Valid @RequestBody TechnicianBody body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addTechnician(body));
    }

    @PutMapping("/technicians/{id}/active")
    public TechnicianResponse setActive(@PathVariable Long id, @RequestParam boolean value) {
        return service.setActive(id, value);
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard() {
        return service.dashboard();
    }
}
