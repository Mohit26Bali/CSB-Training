package com.hackathon.procurement.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.hackathon.procurement.entity.BudgetGate;
import com.hackathon.procurement.repo.BudgetGateRepository;

/** Makes sure the single lock row exists (always, even when demo seeding is off). */
@Component
public class BudgetGateInitializer {

    private final BudgetGateRepository gate;

    public BudgetGateInitializer(BudgetGateRepository gate) {
        this.gate = gate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureGateExists() {
        if (!gate.existsById(BudgetGate.ID)) {
            gate.save(BudgetGate.create());
        }
    }
}
