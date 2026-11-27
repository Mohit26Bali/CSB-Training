package com.hackathon.procurement.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hackathon.procurement.dto.OrderRequest;
import com.hackathon.procurement.dto.ProcurementDashboard;
import com.hackathon.procurement.dto.PurchaseResponse;
import com.hackathon.procurement.dto.RejectRequest;
import com.hackathon.procurement.entity.RequestStatus;
import com.hackathon.procurement.service.ProcurementService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/manager")
public class ManagerRequestController {

    private final ProcurementService service;

    public ManagerRequestController(ProcurementService service) {
        this.service = service;
    }

    @GetMapping("/requests")
    public List<PurchaseResponse> list(@RequestParam(required = false) RequestStatus status,
                                       @RequestParam(required = false) String category,
                                       @RequestParam(required = false) String search) {
        return service.allRequests(status, category, search);
    }

    @PutMapping("/requests/{id}/approve")
    public PurchaseResponse approve(@PathVariable Long id, Authentication authentication) {
        return service.approve(id, authentication.getName());
    }

    @PutMapping("/requests/{id}/reject")
    public PurchaseResponse reject(@PathVariable Long id, @Valid @RequestBody RejectRequest body, Authentication authentication) {
        return service.reject(id, body.reason(), authentication.getName());
    }

    @PutMapping("/requests/{id}/order")
    public PurchaseResponse order(@PathVariable Long id, @Valid @RequestBody(required = false) OrderRequest body) {
        return service.markOrdered(id, body == null ? null : body.orderReference());
    }

    @PutMapping("/requests/{id}/deliver")
    public PurchaseResponse deliver(@PathVariable Long id) {
        return service.markDelivered(id);
    }

    @GetMapping("/dashboard")
    public ProcurementDashboard dashboard() {
        return service.dashboard();
    }
}
