package com.icc.incident.repository;

import com.icc.incident.entity.CommentType;
import com.icc.incident.entity.IncidentComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface IncidentCommentRepository extends JpaRepository<IncidentComment, Long> {

    List<IncidentComment> findByIncidentIdOrderByIdAsc(Long incidentId);

    /** The comments that wait for ServiceNow, oldest first. */
    List<IncidentComment> findTop50BySyncStatusOrderByIdAsc(String syncStatus);

    @Modifying
    @Transactional
    @Query("update IncidentComment c set c.syncStatus = 'SYNCED' where c.id = :id")
    int markSynced(@Param("id") Long id);

    @Modifying
    @Transactional
    @Query("update IncidentComment c set c.syncStatus = :status where c.id = :id")
    int markStatus(@Param("id") Long id, @Param("status") String status);

    List<IncidentComment> findByIncidentIdAndTypeOrderByIdAsc(Long incidentId, CommentType type);
}
