package lib.service;

import lib.model.AuditLog;
import lib.repo.AuditLogRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AuditWriter {
    private final AuditLogRepository audit;

    public AuditWriter(AuditLogRepository audit) { this.audit = audit; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)           // a separate transaction, committed on its own
    public void write(String message) {
        audit.save(new AuditLog(message));
    }
}
