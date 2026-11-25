package lib.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;

/** Uses a SEQUENCE id, so Hibernate can group inserts into JDBC batches (IDENTITY ids switch batching off). */
@Entity
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "audit_seq")
    @SequenceGenerator(name = "audit_seq", sequenceName = "audit_seq", allocationSize = 50)
    private Long id;

    private String message;

    protected AuditLog() { }
    public AuditLog(String message) { this.message = message; }

    public Long getId() { return id; }
    public String getMessage() { return message; }
}
