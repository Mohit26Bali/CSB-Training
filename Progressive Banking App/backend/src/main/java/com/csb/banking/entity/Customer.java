package com.csb.banking.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * The banking profile of a customer (table {@code customers}): contact details from section 5 of the project
 * description. The customer's display name lives on the linked {@link User#getFullName()}.
 *
 * <p>Relations:
 * <ul>
 *   <li>One-to-one with {@link User} (foreign key {@code user_id}, unique).</li>
 *   <li>One-to-many with {@link Account}: the foreign key sits on the account side, so a customer may own
 *       several accounts and this class needs no collection.</li>
 *   <li>Created together with its user by {@code service/AuthService#register}; looked up by username in
 *       {@code service/AccountService} to find out who is opening an account.</li>
 * </ul>
 */
@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 15)
    private String mobile;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Customer() {
        // required by JPA
    }

    public Customer(User user, String email, String mobile, String address) {
        this.user = user;
        this.email = email;
        this.mobile = mobile;
        this.address = address;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getEmail() {
        return email;
    }

    public String getMobile() {
        return mobile;
    }

    public String getAddress() {
        return address;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
