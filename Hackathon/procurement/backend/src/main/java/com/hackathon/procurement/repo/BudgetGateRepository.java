package com.hackathon.procurement.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.hackathon.procurement.entity.BudgetGate;

import jakarta.persistence.LockModeType;

public interface BudgetGateRepository extends JpaRepository<BudgetGate, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from BudgetGate g where g.id = 1")
    Optional<BudgetGate> lock();
}
