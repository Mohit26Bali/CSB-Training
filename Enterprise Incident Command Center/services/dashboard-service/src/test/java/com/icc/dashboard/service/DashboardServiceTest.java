package com.icc.dashboard.service;

import com.icc.common.error.ApiException;
import com.icc.common.error.BadRequestException;
import com.icc.common.error.ErrorCode;
import com.icc.dashboard.client.IncidentStatsClient;
import com.icc.dashboard.client.IncidentStatsClient.DailyCount;
import com.icc.dashboard.client.IncidentStatsClient.IncidentStats;
import com.icc.dashboard.client.MajorIncidentStatsClient;
import com.icc.dashboard.client.MajorIncidentStatsClient.MajorIncidentStats;
import com.icc.dashboard.client.RcaStatsClient;
import com.icc.dashboard.client.RcaStatsClient.ProblemStats;
import com.icc.dashboard.dto.DashboardSummaryResponse;
import com.icc.dashboard.dto.TrendsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("rawtypes")
class DashboardServiceTest {

    // A circuit breaker that only calls the supplier, and uses the fallback when it throws.
    private static final CircuitBreaker PASS_THROUGH = new CircuitBreaker() {
        @Override
        public <T> T run(Supplier<T> toRun, Function<Throwable, T> fallback) {
            try {
                return toRun.get();
            } catch (Exception failure) {
                return fallback.apply(failure);
            }
        }
    };

    @Mock
    private IncidentStatsClient incidentClient;
    @Mock
    private MajorIncidentStatsClient majorIncidentClient;
    @Mock
    private RcaStatsClient rcaClient;
    @Mock
    private CircuitBreakerFactory circuitBreakers;

    private DashboardService service;

    private static final String TOKEN = "Bearer abc";
    private static final String CID = "cid-1";

    @BeforeEach
    void setUp() {
        // lenient: the test that checks the "days" rule never reaches a circuit breaker.
        lenient().when(circuitBreakers.create(anyString())).thenReturn(PASS_THROUGH);
        // Runnable::run executes the "other thread" work right here, so the test is deterministic.
        service = new DashboardService(incidentClient, majorIncidentClient, rcaClient, circuitBreakers, Runnable::run);
    }

    private IncidentStats incidentStats() {
        return new IncidentStats(10, 6, 2, 3, Map.of("NEW", 4L), Map.of(1, 2L),
                List.of(new DailyCount(LocalDate.of(2026, 11, 21), 5, 2),
                        new DailyCount(LocalDate.of(2026, 11, 22), 3, 1)));
    }

    @Test
    void summary_whenEverythingWorks_hasAllThreeParts() {
        when(incidentClient.stats(7, TOKEN, CID)).thenReturn(incidentStats());
        when(majorIncidentClient.stats(TOKEN, CID)).thenReturn(new MajorIncidentStats(2, 1, Map.of("DECLARED", 1L)));
        when(rcaClient.stats(TOKEN, CID)).thenReturn(new ProblemStats(4, 3, 5, Map.of("DRAFT", 3L)));

        DashboardSummaryResponse summary = service.summary(TOKEN, CID);

        assertEquals(6, summary.incidents().open());
        assertEquals(2, summary.incidents().criticalOpen());
        assertEquals(1, summary.majorIncidents().active());
        assertEquals(5, summary.problems().openActions());
        assertTrue(summary.unavailable().isEmpty());
    }

    @Test
    void summary_whenOneServiceIsDown_stillReturnsTheOthers_andNamesTheMissingPart() {
        when(incidentClient.stats(7, TOKEN, CID)).thenReturn(incidentStats());
        when(majorIncidentClient.stats(TOKEN, CID)).thenThrow(new IllegalStateException("major incident service is down"));
        when(rcaClient.stats(TOKEN, CID)).thenReturn(new ProblemStats(4, 3, 5, Map.of()));

        DashboardSummaryResponse summary = service.summary(TOKEN, CID);

        assertNotNull(summary.incidents());
        assertNull(summary.majorIncidents());
        assertNotNull(summary.problems());
        assertEquals(List.of("majorIncidents"), summary.unavailable());
    }

    @Test
    void summary_whenEverythingIsDown_isStillAnAnswer_notAnError() {
        when(incidentClient.stats(7, TOKEN, CID)).thenThrow(new IllegalStateException("down"));
        when(majorIncidentClient.stats(TOKEN, CID)).thenThrow(new IllegalStateException("down"));
        when(rcaClient.stats(TOKEN, CID)).thenThrow(new IllegalStateException("down"));

        DashboardSummaryResponse summary = service.summary(TOKEN, CID);

        assertNull(summary.incidents());
        assertEquals(List.of("incidents", "majorIncidents", "problems"), summary.unavailable());
    }

    @Test
    void trends_returnsTheDailyCounts() {
        when(incidentClient.stats(30, TOKEN, CID)).thenReturn(incidentStats());

        TrendsResponse trends = service.trends(30, TOKEN, CID);

        assertEquals(30, trends.days());
        assertEquals(2, trends.daily().size());
        assertEquals(5, trends.daily().get(0).created());
    }

    @Test
    void trends_onlySevenOrThirtyDaysAreAllowed() {
        assertThrows(BadRequestException.class, () -> service.trends(14, TOKEN, CID));

        verify(incidentClient, never()).stats(14, TOKEN, CID);
    }

    @Test
    void trends_whenTheIncidentServiceIsDown_is503() {
        when(incidentClient.stats(7, TOKEN, CID)).thenThrow(new IllegalStateException("down"));

        ApiException error = assertThrows(ApiException.class, () -> service.trends(7, TOKEN, CID));

        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, error.getErrorCode());
    }
}
