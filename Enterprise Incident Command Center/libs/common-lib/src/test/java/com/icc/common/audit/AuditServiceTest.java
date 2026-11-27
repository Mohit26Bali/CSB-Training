package com.icc.common.audit;

import com.icc.common.jwt.JwtClaims;
import com.icc.common.jwt.Role;
import com.icc.common.page.PageResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditEventRepository repository;

    private AuditService service;

    @BeforeEach
    void setUp() {
        service = new AuditService(repository);
    }

    @AfterEach
    void clean() {
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    private AuditEvent savedEvent() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void explicitActor_isSavedWithCorrelationId() {
        MDC.put("correlationId", "abc-123");

        service.record(7L, "LOGIN", "USER", 7L, "ok");

        AuditEvent event = savedEvent();
        assertEquals(7L, event.getActorId());
        assertEquals("LOGIN", event.getAction());
        assertEquals("USER", event.getEntityType());
        assertEquals(7L, event.getEntityId());
        assertEquals("ok", event.getDetails());
        assertEquals("abc-123", event.getCorrelationId());
    }

    @Test
    void loggedInUser_isReadFromSecurityContext() {
        JwtClaims claims = new JwtClaims(9L, "asha", Role.ADMIN, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(claims, null, List.of()));

        service.record("RULE_CHANGED", "RULE", 3L, "Network to team 2");

        assertEquals(9L, savedEvent().getActorId());
    }

    @Test
    void noLogin_actorIsNull() {
        service.record("SYSTEM_JOB", "INCIDENT", 1L, "x");

        assertNull(savedEvent().getActorId());
    }

    @Test
    void longDetails_areCutTo500Characters() {
        service.record(1L, "A", "B", 1L, "x".repeat(600));

        assertEquals(500, savedEvent().getDetails().length());
    }

    @Test
    void list_mapsTheRepositoryPage() {
        AuditEvent event = new AuditEvent(7L, "LOGIN", "USER", 7L, "ok", "abc");
        when(repository.findAllByOrderByIdDesc(PageRequest.of(0, 2)))
                .thenReturn(new PageImpl<>(List.of(event), PageRequest.of(0, 2), 5));

        PageResponse<AuditEventResponse> response = service.list(0, 2);

        assertEquals(5, response.total());
        assertEquals(0, response.page());
        assertEquals(2, response.size());
        assertEquals(1, response.items().size());
        assertEquals("LOGIN", response.items().get(0).action());
    }
}
