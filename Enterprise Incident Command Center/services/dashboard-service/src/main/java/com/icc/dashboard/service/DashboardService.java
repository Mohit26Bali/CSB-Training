package com.icc.dashboard.service;

import com.icc.common.error.ApiException;
import com.icc.common.error.BadRequestException;
import com.icc.common.error.ErrorCode;
import com.icc.dashboard.client.IncidentStatsClient;
import com.icc.dashboard.client.IncidentStatsClient.IncidentStats;
import com.icc.dashboard.client.MajorIncidentStatsClient;
import com.icc.dashboard.client.MajorIncidentStatsClient.MajorIncidentStats;
import com.icc.dashboard.client.RcaStatsClient;
import com.icc.dashboard.client.RcaStatsClient.ProblemStats;
import com.icc.dashboard.dto.DashboardSummaryResponse;
import com.icc.dashboard.dto.DashboardSummaryResponse.IncidentSummary;
import com.icc.dashboard.dto.DashboardSummaryResponse.MajorIncidentSummary;
import com.icc.dashboard.dto.DashboardSummaryResponse.ProblemSummary;
import com.icc.dashboard.dto.TrendsResponse;
import com.icc.dashboard.dto.TrendsResponse.DailyCount;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Reads the numbers of the Incident, Major Incident and RCA services (ARCHITECTURE: Dashboard calls all three).
 * The three calls run at the same time, each behind its own circuit breaker (D10).
 * If one fails, the summary still contains the other two and names the missing part.
 */
@Service
public class DashboardService {

    private static final int SUMMARY_DAYS = 7;
    private static final Set<Integer> ALLOWED_TREND_DAYS = Set.of(7, 30);

    private final IncidentStatsClient incidentClient;
    private final MajorIncidentStatsClient majorIncidentClient;
    private final RcaStatsClient rcaClient;
    private final CircuitBreakerFactory<?, ?> circuitBreakers;
    private final Executor executor;

    public DashboardService(IncidentStatsClient incidentClient, MajorIncidentStatsClient majorIncidentClient,
                            RcaStatsClient rcaClient, CircuitBreakerFactory<?, ?> circuitBreakers,
                            @Qualifier("dashboardExecutor") Executor executor) {
        this.incidentClient = incidentClient;
        this.majorIncidentClient = majorIncidentClient;
        this.rcaClient = rcaClient;
        this.circuitBreakers = circuitBreakers;
        this.executor = executor;
    }

    public DashboardSummaryResponse summary(String authorization, String correlationId) {
        // Start all three calls first, then wait for them: they run at the same time.
        CompletableFuture<Optional<IncidentStats>> incidents = async("incidents",
                () -> incidentClient.stats(SUMMARY_DAYS, authorization, correlationId));
        CompletableFuture<Optional<MajorIncidentStats>> majorIncidents = async("major-incidents",
                () -> majorIncidentClient.stats(authorization, correlationId));
        CompletableFuture<Optional<ProblemStats>> problems = async("problems",
                () -> rcaClient.stats(authorization, correlationId));

        Optional<IncidentStats> incidentStats = incidents.join();
        Optional<MajorIncidentStats> majorStats = majorIncidents.join();
        Optional<ProblemStats> problemStats = problems.join();

        List<String> unavailable = new ArrayList<>();
        if (incidentStats.isEmpty()) {
            unavailable.add("incidents");
        }
        if (majorStats.isEmpty()) {
            unavailable.add("majorIncidents");
        }
        if (problemStats.isEmpty()) {
            unavailable.add("problems");
        }

        return new DashboardSummaryResponse(
                incidentStats.map(s -> new IncidentSummary(s.total(), s.open(), s.criticalOpen(),
                        s.resolvedToday(), s.byState(), s.byPriority())).orElse(null),
                majorStats.map(s -> new MajorIncidentSummary(s.total(), s.active(), s.byStatus())).orElse(null),
                problemStats.map(s -> new ProblemSummary(s.total(), s.open(), s.openActions(), s.byStatus())).orElse(null),
                unavailable);
    }

    /** The trend needs only the Incident service, so there is no partial result: it either works or fails. */
    public TrendsResponse trends(int days, String authorization, String correlationId) {
        if (!ALLOWED_TREND_DAYS.contains(days)) {
            throw new BadRequestException("days must be 7 or 30");
        }
        IncidentStats stats = circuitBreakers.create("incidents").run(
                () -> incidentClient.stats(days, authorization, correlationId),
                failure -> {
                    throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
                            "Incident data is temporarily unavailable");
                });
        return new TrendsResponse(days, stats.daily().stream()
                .map(day -> new DailyCount(day.date(), day.created(), day.resolved()))
                .toList());
    }

    /** One call on its own thread, behind a circuit breaker. A failure becomes "no value", never an exception. */
    private <T> CompletableFuture<Optional<T>> async(String name, Supplier<T> call) {
        return CompletableFuture.supplyAsync(
                () -> Optional.ofNullable(circuitBreakers.create(name).run(call, failure -> null)),
                executor);
    }
}
