package com.springlab.lab.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

/**
 * Shared by the JPA Relationship Lab entities. Every lab row carries the key of the experiment run that created it, so a
 * run can be cleaned up exactly and can never touch another run or any application data. IDENTITY generation is
 * deliberate: it is what MySQL AUTO_INCREMENT needs, and it makes persist() execute its INSERT immediately (an
 * experiment shows this).
 */
@MappedSuperclass
public abstract class LabBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_key", nullable = false, length = 32, updatable = false)
    private String runKey;

    protected LabBase() {
    }

    protected LabBase(String runKey) {
        this.runKey = runKey;
    }

    public Long getId() {
        return id;
    }

    public String getRunKey() {
        return runKey;
    }
}
