package com.csb.banking.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.csb.banking.entity.IdempotencyRecord;

/**
 * Storage for {@link IdempotencyRecord}. Used only by {@code service/IdempotencyService}.
 */
public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByIdempotencyKeyAndUsername(String idempotencyKey, String username);

    /** Bulk delete of expired keys; must run inside a transaction. */
    @Modifying
    @Query("delete from IdempotencyRecord r where r.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
