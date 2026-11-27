package com.springlab.lab.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A second child of Department, so an experiment can fetch two collections of one parent in a single query. */
@Entity
@Table(name = "jpa_lab_room")
public class Room extends LabBase {

    @Column(nullable = false, length = 80)
    private String label;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    protected Room() {
    }

    public Room(String runKey, String label) {
        super(runKey);
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }
}
