package com.csb.cinescope.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * A CineScope account (table {@code users}).
 *
 * <p>Relations: one user owns many {@link WatchlistItem}s (the foreign key is on the item side). Created by
 * {@code service/AuthService#register}; loaded by {@code service/CustomUserDetailsService} for every login and every
 * token-authenticated request. {@code password} always holds a BCrypt hash, never the plain text. {@code region} is the
 * country (ISO code, India by default) used for streaming availability. {@code role} decides whether the account may manage
 * other accounts. {@code tokenVersion} is bumped whenever the password changes, the role changes or the account is disabled,
 * which makes every login token issued before that moment worthless (see {@code security/JwtAuthenticationFilter}).
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(name = "display_name", nullable = false, length = 60)
    private String displayName;

    @Column(nullable = false, length = 2)
    private String region;

    @Column(nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role = Role.USER;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected User() {
        // required by JPA
    }

    public User(String username, String email, String passwordHash, String displayName, String region) {
        this.username = username;
        this.email = email;
        this.password = passwordHash;
        this.displayName = displayName;
        this.region = region;
    }

    public User(String username, String email, String passwordHash, String displayName, String region, Role role) {
        this(username, email, passwordHash, displayName, region);
        this.role = role;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getRegion() {
        return region;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Role getRole() {
        return role;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    // ---- changes: each one that must end existing sessions bumps the token version ----

    public void updateProfile(String displayName, String region) {
        this.displayName = displayName;
        this.region = region;
    }

    /** Stores a new BCrypt hash and ends every existing session. */
    public void changePassword(String newPasswordHash) {
        this.password = newPasswordHash;
        this.tokenVersion++;
    }

    /** Changes the role; a changed role ends every existing session (their rights are different now). */
    public void changeRole(Role newRole) {
        if (newRole != role) {
            this.role = newRole;
            this.tokenVersion++;
        }
    }

    /** Enables or disables the account; disabling also ends every existing session. */
    public void setEnabled(boolean enabled) {
        if (this.enabled && !enabled) {
            this.tokenVersion++;
        }
        this.enabled = enabled;
    }
}
