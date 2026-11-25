package com.csb.banking.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.csb.banking.exception.TooManyAttemptsException;

/**
 * In-memory brute-force protection: after {@code app.security.max-login-attempts} failed logins a username is
 * locked for {@code app.security.lockout-minutes}.
 *
 * <p>Called only by {@link AuthService#login}: {@code checkNotLocked} before authenticating, {@code recordFailure}
 * or {@code recordSuccess} after. Counters are kept per application instance and are lost on restart, which is
 * enough for this training project; a shared store (database / Redis) would be needed with several instances.
 */
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
