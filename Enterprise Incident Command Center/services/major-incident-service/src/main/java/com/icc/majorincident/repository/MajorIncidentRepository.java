package com.icc.majorincident.repository;

import com.icc.majorincident.entity.MajorIncident;
import com.icc.majorincident.entity.MajorIncidentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MajorIncidentRepository extends JpaRepository<MajorIncident, Long> {

    boolean existsByIncidentId(Long incidentId);

    Page<MajorIncident> findByStatus(MajorIncidentStatus status, Pageable pageable);

    @Query("select m.status, count(m) from MajorIncident m group by m.status")
    List<Object[]> countGroupedByStatus();
}
