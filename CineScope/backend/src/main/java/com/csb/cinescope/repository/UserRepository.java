package com.csb.cinescope.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.csb.cinescope.entity.Role;
import com.csb.cinescope.entity.User;

/**
 * Queries on {@link User}. Used by {@code service/CustomUserDetailsService} (login and every token-authenticated request),
 * {@code service/AuthService} (duplicate checks, current user) and {@code service/WatchlistService} (who owns the list).
 */
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmailIgnoreCase(String email);

    /** How many enabled accounts have this role; used to keep at least one enabled administrator. */
    long countByRoleAndEnabledTrue(Role role);
}
