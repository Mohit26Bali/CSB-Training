package com.icc.common.audit;

import com.icc.common.correlation.CorrelationIds;
import com.icc.common.jwt.JwtClaims;
import com.icc.common.page.PageResponse;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one shared way to write and read the audit trail (D11).
 * A service calls record(...) inside the same transaction as the change it describes,
 * so the change and its audit row are saved together or not at all.
 */
public class AuditService {

    private static final int MAX_DETAILS = 500;

    private final AuditEventRepository repository;

    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
    }

    /** Records an action by the logged-in user (the actor is read from the security context). */
    public void record(String action, String entityType, Long entityId, String details) {
        record(currentUserId(), action, entityType, entityId, details);
    }

    /** Records an action by a given actor. Use null for the system itself. */
    public void record(Long actorId, String action, String entityType, Long entityId, String details) {
        String shortDetails = details != null && details.length() > MAX_DETAILS
                ? details.substring(0, MAX_DETAILS)
                : details;
        repository.save(new AuditEvent(actorId, action, entityType, entityId, shortDetails,
                MDC.get(CorrelationIds.MDC_KEY)));
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> list(int page, int size) {
        Page<AuditEvent> result = repository.findAllByOrderByIdDesc(PageRequest.of(page, size));
        return new PageResponse<>(
                result.getContent().stream().map(AuditService::toResponse).toList(),
                result.getTotalElements(), result.getNumber(), result.getSize());
    }

    private static AuditEventResponse toResponse(AuditEvent e) {
        return new AuditEventResponse(e.getId(), e.getActorId(), e.getAction(), e.getEntityType(),
                e.getEntityId(), e.getDetails(), e.getCorrelationId(), e.getCreatedAt());
    }

    private static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof JwtClaims claims) {
            return claims.userId();
        }
        return null;
    }
}
