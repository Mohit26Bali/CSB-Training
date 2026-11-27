package com.springlab.service;

import com.springlab.entity.Role;
import com.springlab.entity.User;
import com.springlab.exception.BusinessRuleViolationException;
import com.springlab.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deliberately NOT @Transactional: the two threads must run real, separate, committing transactions against MySQL
 * to prove the row locking in {@code findActiveAdminsForUpdate} works. Data is created and removed explicitly in the
 * dedicated test schema.
 */
@SpringBootTest
@ActiveProfiles("test")
class AdminUserConcurrencyIntegrationTest {

    @Autowired
    private AdminUserService adminUserService;
    @Autowired
    private UserRepository userRepository;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        byte[] random = new byte[48];
        new SecureRandom().nextBytes(random);
        String secret = HexFormat.of().formatHex(random);
        registry.add("springlab.jwt.secret", () -> secret);
    }

    @BeforeEach
    @AfterEach
    void clean() {
        userRepository.deleteAll();
    }

    @Test
    void twoAdministratorsDemotingEachOtherAtTheSameTimeCannotLeaveZeroAdministrators() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 5; round++) {
                userRepository.deleteAll();
                User a = userRepository.saveAndFlush(new User("admin_a", "a@example.com", "{noop}x", Role.ADMIN));
                User b = userRepository.saveAndFlush(new User("admin_b", "b@example.com", "{noop}x", Role.ADMIN));

                CyclicBarrier startTogether = new CyclicBarrier(2);
                // A demotes B while B demotes A. Neither is demoting themselves, so only the last-admin rule can stop one.
                Callable<Boolean> aDemotesB = () -> attempt(startTogether, b.getId(), "admin_a");
                Callable<Boolean> bDemotesA = () -> attempt(startTogether, a.getId(), "admin_b");

                List<Future<Boolean>> results = new ArrayList<>(pool.invokeAll(List.of(aDemotesB, bDemotesA), 30,
                        TimeUnit.SECONDS));
                long successes = 0;
                for (Future<Boolean> result : results) {
                    if (result.get()) {
                        successes++;
                    }
                }

                assertThat(successes).as("round %d: exactly one demotion may succeed", round).isEqualTo(1);
                assertThat(userRepository.countByRoleAndEnabled(Role.ADMIN, true))
                        .as("round %d: an active administrator must remain", round).isEqualTo(1);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private boolean attempt(CyclicBarrier barrier, Long targetId, String actingUsername) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        try {
            adminUserService.changeRole(targetId, Role.TEACHER, actingUsername);
            return true;
        } catch (BusinessRuleViolationException e) {
            return false;
        }
    }
}
