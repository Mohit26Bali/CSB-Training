package com.company.jwt.security;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * A deny-list of access-token ids (jti) that were logged out before they expired.
 *
 * A JWT cannot be "deleted": it stays valid until its exp, because the server
 * checks only the signature. To end one early, the server must remember it and
 * refuse it. Each entry is kept only until the token would have expired anyway,
 * so the list stays small (at most the tokens of the last access-token-ttl).
 *
 * LIMITATION (documented): this list is in memory, so it is per server instance
 * and lost on restart. With several instances it would live in a shared store
 * such as Redis, with the same automatic expiry.
 */
@Component
public class RevokedAccessTokens {

    private final Map<String, Instant> revoked = new ConcurrentHashMap<>();
    private final Clock clock;

    public RevokedAccessTokens(Clock clock) {
        this.clock = clock;
    }

    public void revoke(String tokenId, Instant tokenExpiresAt) {
        revoked.put(tokenId, tokenExpiresAt);
        Instant now = clock.instant();
        revoked.values().removeIf(expiry -> expiry.isBefore(now));       // forget tokens that expired anyway
    }

    public boolean isRevoked(String tokenId) {
        return revoked.containsKey(tokenId);
    }
}
