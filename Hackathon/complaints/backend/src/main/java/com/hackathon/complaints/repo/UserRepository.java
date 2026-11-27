package com.hackathon.complaints.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hackathon.complaints.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    List<User> findByRoleOrderByFullnameAsc(com.hackathon.complaints.entity.Role role);

    boolean existsByUsername(String username);

    long countByRole(com.hackathon.complaints.entity.Role role);
}
