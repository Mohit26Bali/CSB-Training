package com.hackathon.hostel.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.hostel.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    /** Row lock on the student: their requests and allocations are changed one at a time. */
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.username = :username")
    Optional<User> findByUsernameForUpdate(@Param("username") String username);

    boolean existsByUsername(String username);

    long countByRole(com.hackathon.hostel.entity.Role role);
}
