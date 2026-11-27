package com.hackathon.maintenance.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hackathon.maintenance.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    long countByRole(com.hackathon.maintenance.entity.Role role);
}
