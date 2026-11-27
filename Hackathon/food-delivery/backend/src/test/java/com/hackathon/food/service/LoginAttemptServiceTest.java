package com.hackathon.food.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.hackathon.food.exception.TooManyAttemptsException;

class LoginAttemptServiceTest {

    LoginAttemptService service() {
        return new LoginAttemptService(3, 6, 15);
    }

    @Test
    void anAttackerIsLockedButTheRealUserOnAnotherAddressIsNot() {
        LoginAttemptService s = service();
        for (int i = 0; i < 3; i++) {
            s.recordFailure("restaurant", "203.0.113.9");
        }
        assertThrows(TooManyAttemptsException.class, () -> s.checkNotLocked("restaurant", "203.0.113.9"));
        assertDoesNotThrow(() -> s.checkNotLocked("restaurant", "198.51.100.4"), "the owner signing in from their own address is not locked out");
    }

    @Test
    void theUsernameIsMatchedIgnoringCaseAndSpaces() {
        LoginAttemptService s = service();
        s.recordFailure("Restaurant", "10.0.0.1");
        s.recordFailure(" restaurant ", "10.0.0.1");
        s.recordFailure("RESTAURANT", "10.0.0.1");
        assertThrows(TooManyAttemptsException.class, () -> s.checkNotLocked("restaurant", "10.0.0.1"));
    }

    @Test
    void oneAddressTryingManyUsernamesIsLockedAsAWhole() {
        LoginAttemptService s = service();
        for (int i = 0; i < 6; i++) {
            s.recordFailure("user" + i, "203.0.113.9");    // two failures at most per username: no pair is locked
        }
        assertThrows(TooManyAttemptsException.class, () -> s.checkNotLocked("someone-new", "203.0.113.9"));
        assertDoesNotThrow(() -> s.checkNotLocked("someone-new", "198.51.100.4"));
    }

    @Test
    void aSuccessfulSignInClearsThatUsersFailuresFromThatAddress() {
        LoginAttemptService s = service();
        s.recordFailure("aarav", "10.0.0.1");
        s.recordFailure("aarav", "10.0.0.1");
        s.recordSuccess("aarav", "10.0.0.1");
        s.recordFailure("aarav", "10.0.0.1");
        s.recordFailure("aarav", "10.0.0.1");
        assertDoesNotThrow(() -> s.checkNotLocked("aarav", "10.0.0.1"), "two failures after a success are below the limit of three");
    }
}
