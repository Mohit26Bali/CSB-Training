package com.csb.banking.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.csb.banking.exception.TooManyAttemptsException;

/** Unit tests of the brute-force protection in {@link LoginAttemptService} (no Spring needed). */
class LoginAttemptServiceTest {

    LoginAttemptService attempts = new LoginAttemptService(3, 15);

    private void fail(String user, int times) {
        for (int i = 0; i < times; i++) {
            attempts.recordFailure(user);
        }
    }

    @Test
    void belowTheLimitLoginStaysAllowed() {
        fail("ravi", 2);
        assertDoesNotThrow(() -> attempts.checkNotLocked("ravi"));
    }

    @Test
    void reachingTheLimitLocksTheUsername() {
        fail("ravi", 3);
        TooManyAttemptsException ex = assertThrows(TooManyAttemptsException.class, () -> attempts.checkNotLocked("ravi"));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("minute"));
    }

    @Test
    void otherUsersAreNotAffected() {
        fail("ravi", 3);
        assertDoesNotThrow(() -> attempts.checkNotLocked("priya"));
    }

    @Test
    void aSuccessfulLoginResetsTheCounter() {
        fail("ravi", 2);
        attempts.recordSuccess("ravi");
        fail("ravi", 2);
        assertDoesNotThrow(() -> attempts.checkNotLocked("ravi"));
    }

    @Test
    void theUsernameIsMatchedIgnoringCaseAndSurroundingSpaces() {
        fail("Ravi", 2);
        fail("  ravi ", 1);
        assertThrows(TooManyAttemptsException.class, () -> attempts.checkNotLocked("RAVI"));
    }

    @Test
    void aZeroLengthLockoutExpiresImmediately() {
        LoginAttemptService instant = new LoginAttemptService(1, 0);
        instant.recordFailure("ravi");
        assertDoesNotThrow(() -> instant.checkNotLocked("ravi"));
    }
}
