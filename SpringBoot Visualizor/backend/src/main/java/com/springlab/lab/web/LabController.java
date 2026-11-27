package com.springlab.lab.web;

import com.springlab.lab.catalog.LifecycleCatalog;
import com.springlab.lab.code.CodeReference;
import com.springlab.lab.code.CodeReferenceService;
import com.springlab.lab.meta.FrameworkMeta;
import com.springlab.lab.meta.FrameworkMetaService;
import com.springlab.lab.trace.LifecycleTrace;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only endpoints of the learning lab (plus trace tickets). Authenticated like every other /api path. */
@RestController
@RequestMapping("/api/lab")
public class LabController {

    /** A ticket to send as X-SpringLab-Trace on exactly one traced request. */
    public record TraceTicket(String ticket, long expiresInSeconds) {
        @Override
        public String toString() {
            return "TraceTicket[ticket=***]";
        }
    }

    private final FrameworkMetaService metaService;
    private final CodeReferenceService codeReferences;
    private final LifecycleCatalog catalog;
    private final LabTraceAccessService traceAccess;

    public LabController(FrameworkMetaService metaService, CodeReferenceService codeReferences,
                         LifecycleCatalog catalog, LabTraceAccessService traceAccess) {
        this.metaService = metaService;
        this.codeReferences = codeReferences;
        this.catalog = catalog;
        this.traceAccess = traceAccess;
    }

    @GetMapping("/meta")
    public FrameworkMeta meta() {
        return metaService.meta();
    }

    @GetMapping("/code-references")
    public List<CodeReference> codeReferences() {
        return codeReferences.all();
    }

    @GetMapping("/lifecycle/catalog")
    public LifecycleCatalog.Catalog lifecycleCatalog() {
        return catalog.catalog();
    }

    /** Step 1 of tracing: the caller (identified by their database id) asks to own the next trace. */
    @PostMapping("/traces/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    public TraceTicket traceTicket(@AuthenticationPrincipal Jwt jwt) {
        return new TraceTicket(traceAccess.issueTicket(jwt.getSubject()), traceAccess.ticketLifetimeSeconds());
    }

    /** Step 3: only the owner can read the trace; anyone else gets the same 404 as for an unknown id. */
    @GetMapping("/traces/{id}")
    public LifecycleTrace trace(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return traceAccess.findOwnedTrace(jwt.getSubject(), id);
    }
}
