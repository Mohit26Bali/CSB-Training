package lib.service;

import java.io.IOException;
import lib.model.Author;
import lib.model.Book;
import lib.repo.AuditLogRepository;
import lib.model.AuditLog;
import lib.repo.AuthorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Methods that show how @Transactional decides between commit and rollback. */
@Service
public class CatalogService {

    private final AuthorRepository authors;
    private final AuditLogRepository audit;
    private final AuditWriter auditWriter;

    public CatalogService(AuthorRepository authors, AuditLogRepository audit, AuditWriter auditWriter) {
        this.authors = authors;
        this.audit = audit;
        this.auditWriter = auditWriter;
    }

    @Transactional
    public void addAuthorThenFailWithRuntimeException(String name) {
        authors.save(new Author(name));
        throw new IllegalStateException("boom");                       // unchecked: the transaction is ROLLED BACK
    }

    @Transactional
    public void addAuthorThenFailWithCheckedException(String name) throws IOException {
        authors.save(new Author(name));
        throw new IOException("disk full");                            // checked: by default the transaction COMMITS anyway
    }

    @Transactional(rollbackFor = IOException.class)
    public void addAuthorThenFailWithCheckedExceptionAndRollbackFor(String name) throws IOException {
        authors.save(new Author(name));
        throw new IOException("disk full");                            // rollbackFor makes it roll back
    }

    /** The audit row is written in its OWN transaction (REQUIRES_NEW), so it survives the outer rollback. */
    @Transactional
    public void addAuthorWithAuditThenFail(String name) {
        authors.save(new Author(name));
        auditWriter.write("tried to add " + name);
        throw new IllegalStateException("boom");
    }

    /** Self-invocation: the call to this.writeAudit() bypasses the proxy, so REQUIRES_NEW has no effect. */
    @Transactional
    public void addAuthorWithSelfCalledAuditThenFail(String name) {
        authors.save(new Author(name));
        writeAudit("tried to add " + name);
        throw new IllegalStateException("boom");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeAudit(String message) {
        audit.save(new AuditLog(message));
    }

    @Transactional(readOnly = true)
    public long countAuthors() {
        return authors.count();
    }
}
