package com.icc.incident.service;

import com.icc.incident.dto.IncidentStatsResponse;
import com.icc.incident.entity.Incident;
import com.icc.incident.entity.IncidentState;
import com.icc.incident.repository.IncidentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentStatsServiceTest {

    @Mock
    private IncidentRepository repository;

    private final LocalDate today = LocalDate.of(2026, 11, 22);

    private Incident createdOn(LocalDate day) {
        Incident incident = new Incident("s", "d", "Network", 2, 2, 3, 6L, 2L);
        ReflectionTestUtils.setField(incident, "createdAt", day.atTime(10, 0));
        return incident;
    }

    @Test
    void stats_combinesTheCountsAndTheDailyBuckets() {
        when(repository.count()).thenReturn(10L);
        when(repository.countByStateIn(any())).thenReturn(6L);
        when(repository.countByPriorityAndStateIn(eq(1), any())).thenReturn(2L);
        when(repository.countByResolvedAtGreaterThanEqual(today.atStartOfDay())).thenReturn(3L);
        when(repository.countGroupedByState()).thenReturn(List.<Object[]>of(
                new Object[]{IncidentState.NEW, 4L}, new Object[]{IncidentState.RESOLVED, 6L}));
        when(repository.countGroupedByPriority()).thenReturn(List.<Object[]>of(
                new Object[]{1, 2L}, new Object[]{3, 8L}));

        LocalDateTime from = today.minusDays(2).atStartOfDay();
        Incident resolvedToday = createdOn(today.minusDays(1));
        resolvedToday.setResolvedAt(today.atTime(9, 0));
        when(repository.findByCreatedAtGreaterThanEqual(from)).thenReturn(List.of(
                createdOn(today), createdOn(today), createdOn(today.minusDays(2)), resolvedToday));
        when(repository.findByResolvedAtGreaterThanEqual(from)).thenReturn(List.of(resolvedToday));

        IncidentStatsResponse stats = new IncidentStatsService(repository).stats(3, today);

        assertEquals(10, stats.total());
        assertEquals(6, stats.open());
        assertEquals(2, stats.criticalOpen());
        assertEquals(3, stats.resolvedToday());
        assertEquals(4L, stats.byState().get("NEW"));
        assertEquals(8L, stats.byPriority().get(3));

        // Three days, oldest first. A day without incidents shows zero.
        assertEquals(3, stats.daily().size());
        assertEquals(today.minusDays(2), stats.daily().get(0).date());
        assertEquals(1, stats.daily().get(0).created());
        assertEquals(1, stats.daily().get(1).created());
        assertEquals(2, stats.daily().get(2).created());
        assertEquals(1, stats.daily().get(2).resolved());
    }

    @Test
    void stats_withNoIncidents_returnsZerosForEveryDay() {
        when(repository.count()).thenReturn(0L);
        when(repository.countByStateIn(any())).thenReturn(0L);
        when(repository.countByPriorityAndStateIn(eq(1), any())).thenReturn(0L);
        when(repository.countByResolvedAtGreaterThanEqual(any())).thenReturn(0L);
        when(repository.countGroupedByState()).thenReturn(List.of());
        when(repository.countGroupedByPriority()).thenReturn(List.of());
        when(repository.findByCreatedAtGreaterThanEqual(any())).thenReturn(List.of());
        when(repository.findByResolvedAtGreaterThanEqual(any())).thenReturn(List.of());

        IncidentStatsResponse stats = new IncidentStatsService(repository).stats(7, today);

        assertEquals(7, stats.daily().size());
        assertEquals(0, stats.daily().get(6).created());
        assertEquals(EnumSet.noneOf(IncidentState.class).size(), stats.byState().size());
    }
}
