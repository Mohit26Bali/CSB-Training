package com.springlab.lab.jwt;

import com.springlab.lab.jwt.LabJwtDtos.InspectTokenRequest;
import com.springlab.lab.jwt.LabJwtDtos.IssueTokenRequest;
import com.springlab.lab.jwt.LabJwtDtos.IssuedLabToken;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The JWT simulator's token workshop. Protected by the application's REAL security chain like any other /api path. */
@RestController
@RequestMapping("/api/lab/jwt")
public class LabJwtController {

    private final LabJwtWorkflowService workflow;
    private final LabJwtInspector inspector;
    private final JwtSimulatorCatalog catalog;
    private final SecurityComponentFactsService facts;

    public LabJwtController(LabJwtWorkflowService workflow, LabJwtInspector inspector, JwtSimulatorCatalog catalog,
                            SecurityComponentFactsService facts) {
        this.workflow = workflow;
        this.inspector = inspector;
        this.catalog = catalog;
        this.facts = facts;
    }

    @PostMapping("/issue")
    public IssuedLabToken issue(@Valid @RequestBody IssueTokenRequest request, @AuthenticationPrincipal Jwt jwt) {
        return workflow.issueFor(jwt.getSubject(), request.variant(), request.claimedRole());
    }

    @PostMapping("/inspect")
    public TokenInspection inspect(@Valid @RequestBody InspectTokenRequest request) {
        return inspector.inspect(request.token());
    }

    @GetMapping("/catalog")
    public JwtSimulatorCatalog.Catalog catalog() {
        return catalog.catalog();
    }

    @GetMapping("/components")
    public SecurityComponentFactsService.Facts components() {
        return facts.facts();
    }
}
