package com.hackathon.helpdesk.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hackathon.helpdesk.dto.CommentRequest;
import com.hackathon.helpdesk.dto.CommentResponse;
import com.hackathon.helpdesk.dto.TicketDetail;
import com.hackathon.helpdesk.dto.TicketRequest;
import com.hackathon.helpdesk.dto.TicketResponse;
import com.hackathon.helpdesk.entity.TicketStatus;
import com.hackathon.helpdesk.service.TicketService;

import jakarta.validation.Valid;

/** Endpoints for everyone signed in; the service decides what each role may do with a ticket. */
@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService service;

    public TicketController(TicketService service) {
        this.service = service;
    }

    static boolean isAgent(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPPORT_AGENT"));
    }

    @PostMapping
    public ResponseEntity<TicketResponse> create(@Valid @RequestBody TicketRequest body, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(authentication.getName(), body));
    }

    @GetMapping("/my")
    public List<TicketResponse> mine(@RequestParam(required = false) TicketStatus status, @RequestParam(required = false) String search,
                                     Authentication authentication) {
        return service.mine(authentication.getName(), status, search);
    }

    @GetMapping("/meta")
    public Map<String, Object> meta() {
        return service.meta();
    }

    @GetMapping("/{id}")
    public TicketDetail detail(@PathVariable Long id, Authentication authentication) {
        return service.detail(id, authentication.getName(), isAgent(authentication));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<CommentResponse> comment(@PathVariable Long id, @Valid @RequestBody CommentRequest body, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.comment(id, authentication.getName(), isAgent(authentication), body.body(), Boolean.TRUE.equals(body.internal())));
    }
}
