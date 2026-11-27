package com.springlab.repository;

import com.springlab.config.JpaAuditingConfig;
import com.springlab.entity.Role;
import com.springlab.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against the real local MySQL (schema springlab_test) with Flyway migrations applied.
 * Each test is rolled back by @DataJpaTest's default transactional behaviour.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void savesUserAndSetsAuditedCreatedAt() {
        User saved = userRepository.saveAndFlush(new User("alice", "alice@example.com", "{bcrypt}hash", Role.STUDENT));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.isEnabled()).isTrue();
        assertThat(userRepository.findByUsername("alice")).get().extracting(User::getRole).isEqualTo(Role.STUDENT);
    }

    @Test
    void rejectsDuplicateUsername() {
        userRepository.saveAndFlush(new User("bob", "bob@example.com", "h", Role.TEACHER));

        assertThatThrownBy(() -> userRepository.saveAndFlush(new User("bob", "other@example.com", "h", Role.STUDENT)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void countsUsersByRole() {
        userRepository.save(new User("t1", "t1@example.com", "h", Role.TEACHER));
        userRepository.save(new User("t2", "t2@example.com", "h", Role.TEACHER));
        userRepository.save(new User("s1", "s1@example.com", "h", Role.STUDENT));

        assertThat(userRepository.countByRole(Role.TEACHER)).isEqualTo(2);
        assertThat(userRepository.existsByEmail("s1@example.com")).isTrue();
        assertThat(userRepository.existsByUsername("nobody")).isFalse();
    }
}
