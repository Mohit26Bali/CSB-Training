package com.icc.assignment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.icc.assignment.entity.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);
}
