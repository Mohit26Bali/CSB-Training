package com.hackathon.library.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.library.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    /** Row lock on the member, so the reservation limit cannot be beaten by two clicks at once. */
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.username = :username")
    Optional<User> findByUsernameForUpdate(@Param("username") String username);

    boolean existsByUsername(String username);

    long countByRole(com.hackathon.library.entity.Role role);
}
