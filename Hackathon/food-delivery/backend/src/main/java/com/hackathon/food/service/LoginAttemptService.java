package com.hackathon.food.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.hackathon.food.exception.TooManyAttemptsException;

/**
 * In-memory brute-force protection with two limits, so that guessing is slowed down without letting a stranger lock a real user out:
 * <ul>
 * <li>too many failures for the same <b>username from the same address</b> lock that pair for a while (the attacker is locked,
 * the real user, coming from another address, is not);</li>
 * <li>too many failures from one <b>address</b>, for any usernames, lock that address (a guesser trying many accounts).</li>
 * </ul>
 * The address is the connection's remote address. Behind a reverse proxy, configure the proxy to pass the client address on
 * (server.forward-headers-strategy) so this is the client and not the proxy. State is lost on restart.
 */
@Service
public class LoginAttemptService {

    private static final class Attempts {
        int failures;
        Instant lockedUntil;
    }

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final int maxAttemptsPerAddress;
    private final Duration lockout;

    public LoginAttemptService(@Value("${app.security.max-login-attempts}") int maxAttempts,
                               @Value("${app.security.max-login-attempts-per-address:20}") int maxAttemptsPerAddress,
                               @Value("${app.security.lockout-minutes}") long lockoutMinutes) {
        this.maxAttempts = maxAttempts;
        this.maxAttemptsPerAddress = maxAttemptsPerAddress;
        this.lockout = Duration.ofMinutes(lockoutMinutes);
    }

    public void checkNotLocked(String username, String address) {
        check(pairKey(username, address));
        check(addressKey(address));
    }

    public void recordFailure(String username, String address) {
        fail(pairKey(username, address), maxAttempts);
        fail(addressKey(address), maxAttemptsPerAddress);
    }

    /** A successful sign-in clears the failures of that username from that address (not the address-wide count). */
    public void recordSuccess(String username, String address) {
        attempts.remove(pairKey(username, address));
    }

    private void check(String key) {
        Attempts a = attempts.get(key);
        if (a == null) {
            return;
        }
        synchronized (a) {
            if (a.lockedUntil != null) {
                if (Instant.now().isBefore(a.lockedUntil)) {
                    long minutes = Math.max(1, Duration.between(Instant.now(), a.lockedUntil).toMinutes() + 1);
                    throw new TooManyAttemptsException("Too many failed login attempts. Try again in " + minutes + " minute(s)");
                }
                a.lockedUntil = null;
                a.failures = 0;
            }
        }
    }

    private void fail(String key, int limit) {
        Attempts a = attempts.computeIfAbsent(key, k -> new Attempts());
        synchronized (a) {
            a.failures++;
            if (a.failures >= limit) {
                a.lockedUntil = Instant.now().plus(lockout);
            }
        }
    }

    private static String pairKey(String username, String address) {
        return "u:" + (username == null ? "" : username.trim().toLowerCase(Locale.ROOT)) + "|" + (address == null ? "" : address);
    }

    private static String addressKey(String address) {
        return "a:" + (address == null ? "" : address);
    }
}
