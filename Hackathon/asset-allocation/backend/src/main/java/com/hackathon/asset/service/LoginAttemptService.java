package com.hackathon.asset.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.hackathon.asset.exception.TooManyAttemptsException;

/** In-memory brute-force protection: too many failures for a username lock it for a while. */
@Service
public class LoginAttemptService {

    private static final class Attempts {
        int failures;
        Instant lockedUntil;
    }

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final Duration lockout;

    public LoginAttemptService(@Value("${app.security.max-login-attempts}") int maxAttempts,
                               @Value("${app.security.lockout-minutes}") long lockoutMinutes) {
        this.maxAttempts = maxAttempts;
        this.lockout = Duration.ofMinutes(lockoutMinutes);
    }

    public void checkNotLocked(String username) {
        Attempts a = attempts.get(key(username));
        if (a == null) {
            return;
        }
        synchronized (a) {
            if (a.lockedUntil != null) {
                if (Instant.now().isBefore(a.lockedUntil)) {
                    long minutes = Math.max(1, Duration.between(Instant.now(), a.lockedUntil).toMinutes() + 1);
                    throw new TooManyAttemptsException(
                            "Too many failed login attempts. Try again in " + minutes + " minute(s)");
                }
                a.lockedUntil = null;
                a.failures = 0;
            }
        }
    }

    public void recordFailure(String username) {
        Attempts a = attempts.computeIfAbsent(key(username), k -> new Attempts());
        synchronized (a) {
            a.failures++;
            if (a.failures >= maxAttempts) {
                a.lockedUntil = Instant.now().plus(lockout);
            }
        }
    }

    public void recordSuccess(String username) {
        attempts.remove(key(username));
    }

    private String key(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
