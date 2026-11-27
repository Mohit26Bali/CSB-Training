package com.icc.incident.service;

import com.icc.incident.dto.IncidentStatsResponse;
import com.icc.incident.dto.IncidentStatsResponse.DailyCount;
import com.icc.incident.entity.Incident;
import com.icc.incident.entity.IncidentState;
import com.icc.incident.repository.IncidentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Numbers for the Dashboard service. Read only. */
@Service
public class IncidentStatsService {

    private static final Set<IncidentState> OPEN_STATES = EnumSet.of(IncidentState.NEW, IncidentState.IN_PROGRESS, IncidentState.ON_HOLD);

    private final IncidentRepository incidentRepository;

    public IncidentStatsService(IncidentRepository incidentRepository) {
        this.incidentRepository = incidentRepository;
    }

    public IncidentStatsResponse stats(int days) {
        return stats(days, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public IncidentStatsResponse stats(int days, LocalDate today) {
        Map<String, Long> byState = new LinkedHashMap<>();
        for (Object[] row : incidentRepository.countGroupedByState()) {
            byState.put(((IncidentState) row[0]).name(), (Long) row[1]);
        }
        Map<Integer, Long> byPriority = new TreeMap<>();
        for (Object[] row : incidentRepository.countGroupedByPriority()) {
            byPriority.put((Integer) row[0], (Long) row[1]);
        }

        // Priority 1 is Critical.
        long criticalOpen = incidentRepository.countByPriorityAndStateIn(1, OPEN_STATES);
        long resolvedToday = incidentRepository.countByResolvedAtGreaterThanEqual(today.atStartOfDay());

        return new IncidentStatsResponse(incidentRepository.count(),
                incidentRepository.countByStateIn(OPEN_STATES), criticalOpen, resolvedToday,
                byState, byPriority, dailyCounts(days, today));
    }

    /** Created and resolved per day for the last N days, oldest first. Days without incidents show 0. */
    private List<DailyCount> dailyCounts(int days, LocalDate today) {
        LocalDate first = today.minusDays(days - 1L);
        Map<LocalDate, Long> created = new TreeMap<>();
        for (Incident incident : incidentRepository.findByCreatedAtGreaterThanEqual(first.atStartOfDay())) {
            created.merge(incident.getCreatedAt().toLocalDate(), 1L, Long::sum);
        }
        Map<LocalDate, Long> resolved = new TreeMap<>();
        for (Incident incident : incidentRepository.findByResolvedAtGreaterThanEqual(first.atStartOfDay())) {
            resolved.merge(incident.getResolvedAt().toLocalDate(), 1L, Long::sum);
        }

        List<DailyCount> result = new ArrayList<>();
        for (LocalDate day = first; !day.isAfter(today); day = day.plusDays(1)) {
            result.add(new DailyCount(day, created.getOrDefault(day, 0L), resolved.getOrDefault(day, 0L)));
        }
        return result;
    }
}
