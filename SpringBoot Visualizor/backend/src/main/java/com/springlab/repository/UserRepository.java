package com.springlab.repository;

import com.springlab.dto.user.UserResponse;
import com.springlab.entity.Role;
import com.springlab.entity.User;
import com.springlab.security.UserAuthInfo;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    long countByRole(Role role);

    long countByRoleAndEnabled(Role role, boolean enabled);

    /** Per-request authorization lookup: three columns, no password hash. */
    @Query("select new com.springlab.security.UserAuthInfo(u.username, u.role, u.enabled) "
            + "from User u where u.username = :username")
    Optional<UserAuthInfo> findAuthInfoByUsername(@Param("username") String username);

    /** The stable database id of a user (used to bind lab traces to their owner). */
    @Query("select u.id from User u where u.username = :username")
    Optional<Long> findIdByUsername(@Param("username") String username);

    /** Exactly the fields of {@link UserResponse}: the password hash and every other column are never selected. */
    @Query("select new com.springlab.dto.user.UserResponse(u.id, u.username, u.email, u.role) "
            + "from User u where u.username = :username")
    Optional<UserResponse> findProfileByUsername(@Param("username") String username);

    /**
     * Locks every active administrator row (SELECT ... FOR UPDATE) until the transaction ends.
     * Concurrent "demote/disable an admin" requests therefore run one after the other and each sees the other's result,
     * which is what makes the "never remove the last active administrator" rule safe under concurrency.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.role = com.springlab.entity.Role.ADMIN and u.enabled = true order by u.id")
    List<User> findActiveAdminsForUpdate();

    @Query("""
            select u from User u
            where (:role is null or u.role = :role)
              and (:q is null or lower(u.username) like lower(concat('%', :q, '%'))
                              or lower(u.email) like lower(concat('%', :q, '%')))
            """)
    Page<User> search(@Param("role") Role role, @Param("q") String q, Pageable pageable);
}
