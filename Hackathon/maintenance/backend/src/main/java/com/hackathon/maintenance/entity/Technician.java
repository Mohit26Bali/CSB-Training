package com.hackathon.maintenance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A person (staff or vendor) a facility manager can assign work to. */
@Entity
@Table(name = "technicians")
public class Technician {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Category trade;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false)
    private boolean active = true;

    protected Technician() {
    }

    public Technician(String name, Category trade, String phone) {
        this.name = name;
        this.trade = trade;
        this.phone = phone;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Category getTrade() {
        return trade;
    }

    public String getPhone() {
        return phone;
    }

    public boolean isActive() {
        return active;
    }
}
