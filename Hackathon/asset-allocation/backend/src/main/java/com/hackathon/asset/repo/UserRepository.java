package com.hackathon.asset.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.asset.entity.Role;
import com.hackathon.asset.entity.User;

import jakarta.persistence.LockModeType;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    long countByRole(Role role);

    List<User> findByRoleOrderByFullnameAsc(Role role);

    /** Row lock on the employee, so the limit on how many assets one person holds is checked one request at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.username = :username")
    Optional<User> findByUsernameForUpdate(@Param("username") String username);
}
