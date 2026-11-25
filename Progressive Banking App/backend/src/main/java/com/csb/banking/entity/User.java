package com.csb.banking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A login identity (table {@code users}). Holds credentials and the {@link Role}; nothing banking-specific.
 *
 * <p>Relations:
 * <ul>
 *   <li>A CUSTOMER user has exactly one {@link Customer} profile (the profile owns the foreign key).
 *       An ADMIN user has none.</li>
 *   <li>Loaded by {@code repository/UserRepository}; adapted for Spring Security by
 *       {@code service/CustomUserDetailsService}; created by {@code service/AuthService} (registration) and
 *       {@code config/DataInitializer} (demo data).</li>
 *   <li>{@code password} always holds a BCrypt hash, never plain text.</li>
 * </ul>
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean enabled = true;

    protected User() {
        // required by JPA
    }

    public User(String username, String passwordHash, String fullName, Role role) {
        this.username = username;
        this.password = passwordHash;
        this.fullName = fullName;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getFullName() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
