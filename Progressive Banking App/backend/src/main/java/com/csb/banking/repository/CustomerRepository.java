package com.csb.banking.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.csb.banking.entity.Customer;

/**
 * Queries on {@link Customer}.
 *
 * <p>{@code findByUserUsername} follows the relation Customer -> User ("user.username") and is how
 * {@code service/AccountService} turns the logged-in username (from the JWT) into the customer profile.
 * {@code existsByEmail} backs the duplicate check in {@code service/AuthService#register}.
 */
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByUserUsername(String username);

    boolean existsByEmailIgnoreCase(String email);
}
