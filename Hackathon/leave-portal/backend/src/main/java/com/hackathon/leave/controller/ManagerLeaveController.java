package com.hackathon.leave.controller;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hackathon.leave.dto.DecisionNote;
import com.hackathon.leave.dto.LeaveResponse;
import com.hackathon.leave.entity.LeaveStatus;
import com.hackathon.leave.exception.FieldValidationException;
import com.hackathon.leave.service.LeaveService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/manager/leave")
public class ManagerLeaveController {

    private final LeaveService service;

    public ManagerLeaveController(LeaveService service) {
        this.service = service;
    }

    @GetMapping
    public List<LeaveResponse> list(@RequestParam(required = false) LeaveStatus status,
                                    @RequestParam(required = false) String type,
                                    @RequestParam(required = false) String search) {
        return service.allLeave(status, type, search);
    }

    @PutMapping("/{id}/approve")
    public LeaveResponse approve(@PathVariable Long id, @Valid @RequestBody(required = false) DecisionNote body,
                                 Authentication authentication) {
        return service.approve(id, authentication.getName(), body == null ? null : body.note());
    }

    @PutMapping("/{id}/reject")
    public LeaveResponse reject(@PathVariable Long id, @Valid @RequestBody(required = false) DecisionNote body,
                                Authentication authentication) {
        return service.reject(id, authentication.getName(), body == null ? null : body.note());
    }

    /** Approved leave in a month (yyyy-MM, default: this month). */
    @GetMapping("/calendar")
    public List<LeaveResponse> calendar(@RequestParam(required = false) String month) {
        try {
            return service.calendar(month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month.trim()));
        } catch (DateTimeParseException ex) {
            throw new FieldValidationException("month", "Month must look like 2026-10");
        }
    }
}
