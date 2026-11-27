package com.hackathon.vehicle.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The service center itself: a single row (id 1) holding the number of bays. Locking it makes all booking changes happen one at a time. */
@Entity
@Table(name = "service_center")
public class Center {

    public static final long ID = 1L;

    @Id
    private Long id = ID;

    private int bays;

    protected Center() {
    }

    public Center(int bays) {
        this.bays = bays;
    }

    public int getBays() {
        return bays;
    }

    public void setBays(int bays) {
        this.bays = bays;
    }
}
