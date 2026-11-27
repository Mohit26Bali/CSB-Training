package com.hackathon.appointment.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.appointment.entity.Provider;

import jakarta.persistence.LockModeType;

public interface ProviderRepository extends JpaRepository<Provider, Long> {

    @EntityGraph(attributePaths = "user")
    List<Provider> findAllByOrderByIdAsc();

    @EntityGraph(attributePaths = "user")
    Optional<Provider> findWithUserById(Long id);

    @EntityGraph(attributePaths = "user")
    @Query("select p from Provider p where p.user.username = :username")
    Optional<Provider> findByUsername(@Param("username") String username);

    /** Row lock: a provider's slots are created one request at a time, so two requests cannot create overlapping slots. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Provider p where p.user.username = :username")
    Optional<Provider> findByUsernameForUpdate(@Param("username") String username);
}
