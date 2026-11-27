package com.icc.incident.repository;

import com.icc.incident.entity.Incident;
import com.icc.incident.entity.IncidentState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface IncidentRepository extends JpaRepository<Incident, Long>, JpaSpecificationExecutor<Incident> {

    long countByStateIn(Collection<IncidentState> states);

    /** The next incidents that wait for ServiceNow, oldest first. */
    List<Incident> findTop20BySyncStatusOrderByIdAsc(String syncStatus);

    // The sync job writes with updates that name only the sync columns, so it never overwrites a change the user made
    // while ServiceNow was being called. They are bulk updates: no @PreUpdate, so "last updated" does not move.

    /** Remembers ServiceNow's id and number, whatever happened to the rest. */
    @Modifying
    @Transactional
    @Query("update Incident i set i.externalId = :externalId, i.externalNumber = :externalNumber, i.correlationId = :correlationId where i.id = :id")
    int saveExternalIds(@Param("id") Long id, @Param("externalId") String externalId,
                        @Param("externalNumber") String externalNumber, @Param("correlationId") String correlationId);

    /** SYNCED, but only if the incident is still the version that was sent (its updatedAt has not moved). */
    @Modifying
    @Transactional
    @Query("update Incident i set i.syncStatus = 'SYNCED', i.lastSyncedAt = :now, i.lastSyncError = null where i.id = :id and i.updatedAt = :readAt")
    int markSynced(@Param("id") Long id, @Param("readAt") LocalDateTime readAt, @Param("now") LocalDateTime now);

    /** A failure: FAILED (will not be retried) or PENDING (will), with the reason; only if the version is unchanged. */
    @Modifying
    @Transactional
    @Query("update Incident i set i.syncStatus = :status, i.lastSyncError = :error where i.id = :id and i.updatedAt = :readAt")
    int markSyncResult(@Param("id") Long id, @Param("readAt") LocalDateTime readAt, @Param("status") String status, @Param("error") String error);

    long countByPriorityAndStateIn(int priority, Collection<IncidentState> states);

    long countByResolvedAtGreaterThanEqual(LocalDateTime from);

    List<Incident> findByCreatedAtGreaterThanEqual(LocalDateTime from);

    List<Incident> findByResolvedAtGreaterThanEqual(LocalDateTime from);

    @Query("select i.state, count(i) from Incident i group by i.state")
    List<Object[]> countGroupedByState();

    @Query("select i.priority, count(i) from Incident i group by i.priority")
    List<Object[]> countGroupedByPriority();
}
