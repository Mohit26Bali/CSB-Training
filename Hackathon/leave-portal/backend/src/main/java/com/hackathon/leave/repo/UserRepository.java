package com.hackathon.leave.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hackathon.leave.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    long countByRole(com.hackathon.leave.entity.Role role);
}
