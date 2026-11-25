package com.csb.cinescope.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.csb.cinescope.exception.TooManyAttemptsException;

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
        fail("asha", 2);
        assertDoesNotThrow(() -> attempts.checkNotLocked("asha"));
    }

    @Test
    void reachingTheLimitLocksTheUsernameAndSaysForHowLong() {
        fail("asha", 3);
        TooManyAttemptsException ex = assertThrows(TooManyAttemptsException.class, () -> attempts.checkNotLocked("asha"));
        assertTrue(ex.getMessage().contains("minute"));
    }

    @Test
    void otherUsersAreNotAffected() {
        fail("asha", 3);
        assertDoesNotThrow(() -> attempts.checkNotLocked("ravi"));
    }

    @Test
    void aSuccessfulLoginResetsTheCounter() {
        fail("asha", 2);
        attempts.recordSuccess("asha");
        fail("asha", 2);
        assertDoesNotThrow(() -> attempts.checkNotLocked("asha"));
    }

    @Test
    void theUsernameIsMatchedIgnoringCaseAndSurroundingSpaces() {
        fail("Asha", 2);
        fail("  asha ", 1);
        assertThrows(TooManyAttemptsException.class, () -> attempts.checkNotLocked("ASHA"));
    }

    @Test
    void aZeroLengthLockoutExpiresImmediately() {
        LoginAttemptService instant = new LoginAttemptService(1, 0);
        instant.recordFailure("asha");
        assertDoesNotThrow(() -> instant.checkNotLocked("asha"));
    }
}
