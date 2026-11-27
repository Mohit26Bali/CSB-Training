package com.hackathon.procurement.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A single-row table that exists only to be locked. Every approval takes this lock before checking the monthly
 * budget, so two managers approving at the same moment cannot both squeeze under the limit.
 */
@Entity
@Table(name = "budget_gate")
public class BudgetGate {

    public static final long ID = 1L;

    @Id
    private Long id = ID;

    protected BudgetGate() {
    }

    public static BudgetGate create() {
        return new BudgetGate();
    }

    public Long getId() {
        return id;
    }
}
