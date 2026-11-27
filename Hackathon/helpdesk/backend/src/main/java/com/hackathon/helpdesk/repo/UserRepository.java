package com.hackathon.helpdesk.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hackathon.helpdesk.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    List<User> findByRoleOrderByFullnameAsc(com.hackathon.helpdesk.entity.Role role);

    boolean existsByUsername(String username);

    long countByRole(com.hackathon.helpdesk.entity.Role role);
}
