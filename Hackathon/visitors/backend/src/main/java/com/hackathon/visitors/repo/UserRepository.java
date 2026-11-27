package com.hackathon.visitors.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.visitors.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    /** Row lock on the host, so a double-clicked invitation cannot create two. */
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.username = :username")
    Optional<User> findByUsernameForUpdate(@Param("username") String username);

    boolean existsByUsername(String username);

    long countByRole(com.hackathon.visitors.entity.Role role);
}
