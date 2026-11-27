package com.cartnest.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.cartnest.entity.Role;
import com.cartnest.entity.User;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    long countByRole(Role role);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
