package com.springlab.lab;

import com.springlab.lab.trace.CodeRefIds;
import com.springlab.lab.trace.Layer;
import com.springlab.lab.trace.LifecycleStepAssembler;
import com.springlab.lab.trace.LifecycleTrace;
import com.springlab.lab.trace.LifecycleTrace.TraceStep;
import com.springlab.lab.trace.Phase;
import com.springlab.lab.trace.Provenance;
import com.springlab.lab.trace.RawTrace;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure unit tests of the rules that decide which provenance each step may claim. */
class LifecycleStepAssemblerTest {

    static final Set<String> NODES = Set.of("client", "securityFilters", "dispatcher", "controller", "service",
            "repository", "hibernate", "database");
    static final Set<String> EDGES = Set.of("client->securityFilters", "securityFilters->dispatcher",
            "securityFilters->repository", "dispatcher->controller", "controller->service", "service->repository",
            "repository->hibernate", "hibernate->database");

    private final LifecycleStepAssembler assembler = new LifecycleStepAssembler();

    private static RawTrace.CallEvent call(int index, Layer layer, String type, String method, Phase phase,
                                           String tx, String thrown) {
        return new RawTrace.CallEvent(index, layer, type, method, phase, index * 1000L, 5_000L, tx, thrown, 0);
    }

    private static RawTrace success() {
        return new RawTrace("GET", "/api/auth/me", true,
                List.of("BearerTokenAuthenticationFilter", "ExceptionTranslationFilter", "AuthorizationFilter"),
                new RawTrace.HandlerInfo("AuthController", "me", "/api/auth/me"),
                new RawTrace.AuthInfo("sam", List.of("ROLE_STUDENT")),
                List.of(call(0, Layer.REPOSITORY, "UserRepository", "findAuthInfoByUsername", Phase.SECURITY, null, null),
                        call(1, Layer.SERVICE, "AuthService", "currentUser", Phase.HANDLER, "declared, readOnly = true", null),
                        call(2, Layer.REPOSITORY, "UserRepository", "findByUsername", Phase.HANDLER, null, null)),
                List.of(new RawTrace.SqlEvent("select ... from users where username=?", Phase.SECURITY, 0,
                                "UserRepository.findAuthInfoByUsername", 1_000),
                        new RawTrace.SqlEvent("select ... from users where username=?", Phase.HANDLER, 2,
                                "UserRepository.findByUsername", 2_000)),
                200, "application/json", 9_000_000);
    }

    private static RawTrace rejected(int status) {
        return new RawTrace("GET", "/api/auth/me", false, List.of("BearerTokenAuthenticationFilter"), null, null,
                List.of(), List.of(), status, "application/problem+json", 1_000_000);
    }

    private static List<String> ids(LifecycleTrace trace) {
        return trace.steps().stream().map(TraceStep::id).toList();
    }

    @Test
    void successfulRequestProducesTheFullOrderedPath() {
        LifecycleTrace trace = assembler.assemble("t1", success());

        assertThat(ids(trace)).containsExactly("client-request", "security-filter-chain", "auth-lookup-0", "sql-0",
                "database-0", "dispatch", "controller", "service-1", "repository-2", "sql-2", "database-2",
                "serialization", "response");
        assertThat(trace.sqlCount()).isEqualTo(2);
        assertThat(trace.handlerInvoked()).isTrue();
        assertThat(trace.sql()).extracting(LifecycleTrace.SqlStatement::phase).containsExactly("SECURITY", "HANDLER");
    }

    @Test
    void provenanceIsAssignedHonestly() {
        LifecycleTrace trace = assembler.assemble("t1", success());

        assertThat(provenance(trace, "client-request")).isEqualTo(Provenance.CAPTURED);
        assertThat(provenance(trace, "sql-0")).isEqualTo(Provenance.CAPTURED);
        assertThat(provenance(trace, "service-1")).isEqualTo(Provenance.CAPTURED);
        // Nothing about MySQL's execution is observed, so these steps must never be CAPTURED/LIVE.
        assertThat(provenance(trace, "database-0")).isEqualTo(Provenance.CONCEPTUAL);
        assertThat(provenance(trace, "database-2")).isEqualTo(Provenance.CONCEPTUAL);
        // The converter class is inferred from the content type only.
        assertThat(provenance(trace, "serialization")).isEqualTo(Provenance.DERIVED);
    }

    @Test
    void sqlTextIsAttachedOnlyToCapturedSteps() {
        for (RawTrace raw : List.of(success(), rejected(401), rejected(403), notFound())) {
            LifecycleTrace trace = assembler.assemble("t", raw);
            for (TraceStep step : trace.steps()) {
                if (!step.sql().isEmpty()) {
                    assertThat(step.provenance()).as("step %s carries SQL", step.id()).isEqualTo(Provenance.CAPTURED);
                }
            }
            // Every captured statement appears in exactly one step.
            long inSteps = trace.steps().stream().mapToLong(s -> s.sql().size()).sum();
            assertThat(inSteps).isEqualTo(trace.sqlCount());
        }
    }

    @Test
    void securityRejectionNeverShowsApplicationLayers() {
        for (int status : new int[]{401, 403}) {
            LifecycleTrace trace = assembler.assemble("t", rejected(status));

            assertThat(trace.handlerInvoked()).isFalse();
            assertThat(ids(trace)).containsExactly("client-request", "security-filter-chain",
                    "rejected-before-controller", "rejection-mechanism", "response");
            assertThat(provenance(trace, "rejected-before-controller")).isEqualTo(Provenance.CAPTURED);
            // WHICH class wrote the response is an inference from status + content type, not an observation.
            assertThat(provenance(trace, "rejection-mechanism")).isEqualTo(Provenance.DERIVED);
            assertThat(trace.steps()).noneMatch(s -> s.nodes().contains("controller") || s.nodes().contains("service")
                    || s.nodes().contains("repository") || s.nodes().contains("database"));
        }
        assertThat(assembler.assemble("t", rejected(401)).steps().get(3).codeRefId()).isEqualTo(CodeRefIds.ENTRY_POINT);
        assertThat(assembler.assemble("t", rejected(403)).steps().get(3).codeRefId())
                .isEqualTo(CodeRefIds.ACCESS_DENIED_HANDLER);
    }

    private static RawTrace notFound() {
        return new RawTrace("GET", "/api/challenges/0", true, List.of("AuthorizationFilter"),
                new RawTrace.HandlerInfo("ChallengeController", "get", "/api/challenges/{id}"),
                new RawTrace.AuthInfo("sam", List.of("ROLE_STUDENT")),
                List.of(call(0, Layer.SERVICE, "ChallengeService", "get", Phase.HANDLER, "declared, readOnly = true",
                        "ResourceNotFoundException")),
                List.of(), 404, "application/problem+json", 2_000_000);
    }

    @Test
    void serviceExceptionProducesADerivedErrorHandlingStepInsteadOfSerialization() {
        LifecycleTrace trace = assembler.assemble("t", notFound());

        assertThat(ids(trace)).contains("exception-handling").doesNotContain("serialization");
        assertThat(provenance(trace, "exception-handling")).isEqualTo(Provenance.DERIVED);
        assertThat(trace.steps()).filteredOn(s -> s.id().equals("exception-handling")).first()
                .satisfies(s -> assertThat(s.codeRefId()).isEqualTo(CodeRefIds.NOT_FOUND_HANDLER));
        assertThat(trace.steps()).filteredOn(s -> s.id().equals("service-0")).first()
                .satisfies(s -> assertThat(s.evidence()).anyMatch(e -> e.label().equals("Threw")
                        && e.value().equals("ResourceNotFoundException")));
    }

    @Test
    void tokenAndHeaderValuesAreNeverPartOfTheEvidence() {
        LifecycleTrace trace = assembler.assemble("t", success());
        String all = trace.steps().toString();

        assertThat(all).contains("present (value not recorded)").doesNotContain("Bearer ").doesNotContain("eyJ");
    }

    @Test
    void everyStepUsesKnownNodesEdgesAndCodeReferences() {
        for (RawTrace raw : List.of(success(), rejected(401), rejected(403), notFound())) {
            LifecycleTrace trace = assembler.assemble("t", raw);
            Set<String> seenIds = new HashSet<>();
            for (TraceStep step : trace.steps()) {
                assertThat(seenIds.add(step.id())).as("duplicate step id %s", step.id()).isTrue();
                assertThat(NODES).containsAll(step.nodes());
                assertThat(step.direction()).isIn("REQUEST", "RESPONSE");
                if (step.edgeFrom() != null) {
                    assertThat(EDGES).contains(step.edgeFrom() + "->" + step.edgeTo());
                }
                if (step.codeRefId() != null) {
                    assertThat(CodeRefIds.ALL).contains(step.codeRefId());
                }
                assertThat(step.explanation()).isNotBlank();
            }
        }
    }

    @Test
    void limitationsAreAlwaysDisclosed() {
        LifecycleTrace trace = assembler.assemble("t", success());

        assertThat(trace.limitations()).anyMatch(l -> l.contains("placeholders") && l.contains("database"));
        assertThat(trace.mode()).isEqualTo("LIVE");
    }

    private static Provenance provenance(LifecycleTrace trace, String stepId) {
        return trace.steps().stream().filter(s -> s.id().equals(stepId)).findFirst().orElseThrow().provenance();
    }
}
