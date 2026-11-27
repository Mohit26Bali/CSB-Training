package com.icc.assignment.entity;

import java.time.LocalDateTime;

import com.icc.common.jwt.Role;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String passwordHash;
    private String fullName;
    private String email;

    @Enumerated(EnumType.STRING)
    private Role role;

    private Long teamId;
    private boolean active;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    private String externalId;

    protected AppUser() {
    }

    public AppUser(Long id, String username, String passwordHash, String fullName, String email,
                   Role role, Long teamId, boolean active) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
        this.teamId = teamId;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }

    public Long getTeamId() {
        return teamId;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getExternalId() {
        return externalId;
    }
}
