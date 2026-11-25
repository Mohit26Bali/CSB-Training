package com.cartnest.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.cartnest.exception.TooManyAttemptsException;

/**
 * Brute-force protection: after app.login.max-failures wrong passwords in a row, the email is locked for
 * app.login.lock-duration. Kept in memory, so it resets when the server restarts.
 */
@Service
public class LoginAttemptService {

    private record Attempts(int failures, Instant lockedUntil) {
    }

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final Duration lockDuration;

    public LoginAttemptService(@Value("${app.login.max-failures}") int maxFailures,
                               @Value("${app.login.lock-duration}") Duration lockDuration) {
        this.maxFailures = maxFailures;
        this.lockDuration = lockDuration;
    }

    public void checkNotLocked(String email) {
        Attempts a = attempts.get(email);
        if (a != null && a.lockedUntil() != null) {
            if (Instant.now().isBefore(a.lockedUntil())) {
                long minutes = Math.max(1, Duration.between(Instant.now(), a.lockedUntil()).toMinutes() + 1);
                throw new TooManyAttemptsException(
                        "Too many failed attempts. Try again in " + minutes + " minute(s)");
            }
            attempts.remove(email);
        }
    }

    public void recordFailure(String email) {
        attempts.compute(email, (key, old) -> {
            int failures = (old == null ? 0 : old.failures()) + 1;
            return new Attempts(failures, failures >= maxFailures ? Instant.now().plus(lockDuration) : null);
        });
    }

    public void recordSuccess(String email) {
        attempts.remove(email);
    }
}
