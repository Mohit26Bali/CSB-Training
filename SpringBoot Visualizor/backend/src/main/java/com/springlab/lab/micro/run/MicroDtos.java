package com.springlab.lab.micro.run;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.springlab.lab.micro.core.CallRecord;
import com.springlab.lab.micro.core.ServiceName;
import com.springlab.lab.micro.core.Span;
import com.springlab.lab.trace.LifecycleTrace.SqlStatement;
import com.springlab.lab.trace.LifecycleTrace.TraceStep;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** API shapes of the Microservices Lab. Nothing here carries a credential, a token, a connection URL or a bound value. */
public final class MicroDtos {

    private MicroDtos() {
    }

    /** The only input of a run: an experiment id and What-If values, which are validated against that experiment's options. */
    public record CreateRunRequest(
            @NotBlank @Size(max = 64) @Pattern(regexp = "[a-z0-9-]+") String experimentId,
            @Size(max = 8) Map<@Size(max = 40) String, @Size(max = 32) String> whatIf) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RunView(String runId, String experimentId, String state, Map<String, String> whatIf, Instant createdAt, Instant updatedAt,
                          MicroResult result) {
    }

    /** What an executed run produced. {@code cleanupRowsRemoved} is the number of lab rows deleted when it ended. */
    public record MicroResult(String environment, String errorCode, long durationMillis, int sqlCount, List<SqlStatement> sql,
                              List<TraceStep> steps, List<Span> spans, List<CallRecord> calls, int cleanupRowsRemoved, List<String> limitations) {
    }

    public record TopicInfo(String id, String title, String summary) {
    }

    public record ServiceInfo(String id, String title, String responsibility, List<String> ownedTables, String databaseNode) {
        static ServiceInfo of(ServiceName s) {
            return new ServiceInfo(s.id(), s.title(), s.responsibility(), s.ownedTables(), s.databaseNode());
        }
    }

    public record Limits(int maxConcurrentRuns, int maxRunsPerUser, int runLifetimeMinutes, int maxCallsPerRun) {
    }

    public record Meta(String environment, List<ServiceInfo> services, Limits limits, List<String> limitations) {
    }
}
