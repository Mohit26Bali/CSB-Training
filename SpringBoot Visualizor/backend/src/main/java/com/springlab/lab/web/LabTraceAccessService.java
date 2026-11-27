package com.springlab.lab.web;

import com.springlab.exception.ResourceNotFoundException;
import com.springlab.lab.trace.LifecycleTrace;
import com.springlab.lab.trace.TraceStore;
import com.springlab.lab.trace.TraceTicketStore;
import com.springlab.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Who may start a trace and who may read one: always the same authenticated user, identified by database id. */
@Service
public class LabTraceAccessService {

    private final UserRepository userRepository;
    private final TraceTicketStore tickets;
    private final TraceStore traces;

    public LabTraceAccessService(UserRepository userRepository, TraceTicketStore tickets, TraceStore traces) {
        this.userRepository = userRepository;
        this.tickets = tickets;
        this.traces = traces;
    }

    /** A single-use ticket that makes the caller the owner of the next trace they start. */
    @Transactional(readOnly = true)
    public String issueTicket(String username) {
        return tickets.issue(userId(username));
    }

    public long ticketLifetimeSeconds() {
        return tickets.timeToLive().toSeconds();
    }

    /** 404 for an unknown, expired or malformed id AND for someone else's trace: the three are indistinguishable. */
    @Transactional(readOnly = true)
    public LifecycleTrace findOwnedTrace(String username, String id) {
        try {
            UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("Trace not found");
        }
        return traces.findOwned(id, userId(username))
                .orElseThrow(() -> new ResourceNotFoundException("Trace not found or expired"));
    }

    private long userId(String username) {
        return userRepository.findIdByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("User no longer exists"));
    }
}
