package com.hackathon.vehicle.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.hackathon.vehicle.entity.Center;

import jakarta.persistence.LockModeType;

public interface CenterRepository extends JpaRepository<Center, Long> {

    /** Row lock: bookings and status changes are handled one at a time, so a bay or a vehicle can never be double booked. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Center c where c.id = 1")
    Optional<Center> lockCenter();
}
