package com.csb.banking.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.csb.banking.entity.User;

/**
 * Queries on {@link User}.
 *
 * <p>Used by {@code service/CustomUserDetailsService} (load credentials during login and on every JWT-authenticated
 * request), {@code service/AuthService} (duplicate-username check, current user) and
 * {@code config/DataInitializer} (is the database empty?).
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);
}
