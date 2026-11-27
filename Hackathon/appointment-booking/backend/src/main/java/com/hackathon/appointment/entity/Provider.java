package com.hackathon.appointment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** The public profile of a service provider account: what they do and where. */
@Entity
@Table(name = "providers")
public class Provider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, length = 60)
    private String title;

    @Column(length = 400)
    private String description;

    @Column(length = 100)
    private String location;

    protected Provider() {
    }

    public Provider(User user, String title, String description, String location) {
        this.user = user;
        update(title, description, location);
    }

    public void update(String title, String description, String location) {
        this.title = title;
        this.description = description;
        this.location = location;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getLocation() {
        return location;
    }
}
