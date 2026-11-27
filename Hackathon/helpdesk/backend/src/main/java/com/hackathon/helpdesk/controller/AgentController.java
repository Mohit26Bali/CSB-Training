package com.hackathon.helpdesk.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hackathon.helpdesk.dto.AgentDashboard;
import com.hackathon.helpdesk.dto.AgentResponse;
import com.hackathon.helpdesk.dto.AssignRequest;
import com.hackathon.helpdesk.dto.StatusRequest;
import com.hackathon.helpdesk.dto.TicketResponse;
import com.hackathon.helpdesk.entity.Priority;
import com.hackathon.helpdesk.entity.TicketStatus;
import com.hackathon.helpdesk.service.TicketService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final TicketService service;

    public AgentController(TicketService service) {
        this.service = service;
    }

    @GetMapping("/tickets")
    public List<TicketResponse> queue(@RequestParam(required = false) TicketStatus status, @RequestParam(required = false) Priority priority,
                                      @RequestParam(required = false) String assignee, @RequestParam(required = false) String search,
                                      Authentication authentication) {
        return service.queue(authentication.getName(), status, priority, assignee, search);
    }

    @PutMapping("/tickets/{id}/assign")
    public TicketResponse assign(@PathVariable Long id, @RequestBody AssignRequest body) {
        return service.assign(id, body.assignee());
    }

    @PutMapping("/tickets/{id}/status")
    public TicketResponse status(@PathVariable Long id, @Valid @RequestBody StatusRequest body, Authentication authentication) {
        return service.updateStatus(id, body.status(), body.note(), authentication.getName());
    }

    @GetMapping("/agents")
    public List<AgentResponse> agents() {
        return service.agents();
    }

    @GetMapping("/dashboard")
    public AgentDashboard dashboard(Authentication authentication) {
        return service.dashboard(authentication.getName());
    }
}
