package com.hackathon.expense.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.expense.entity.Expense;
import com.hackathon.expense.entity.User;

import jakarta.persistence.LockModeType;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    @EntityGraph(attributePaths = "employee")
    List<Expense> findByEmployeeOrderBySubmittedAtDesc(User employee);

    @EntityGraph(attributePaths = "employee")
    List<Expense> findAllByOrderBySubmittedAtDesc();

    /** Row lock so two managers cannot process the same expense at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Expense e where e.id = :id")
    Optional<Expense> findByIdForUpdate(@Param("id") Long id);
}
