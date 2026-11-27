package com.icc.majorincident.repository;

import com.icc.majorincident.entity.TimelineEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TimelineEntryRepository extends JpaRepository<TimelineEntry, Long> {

    List<TimelineEntry> findByMajorIncidentIdOrderByIdAsc(Long majorIncidentId);
}
