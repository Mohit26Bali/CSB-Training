package com.company.jwt.config;

import java.time.Duration;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Every JWT setting is EXTERNAL (portal.jwt.* in application.properties, the
 * keys in the git-ignored jwt-secret.properties). Each one can also be set as
 * an environment variable in production, e.g. PORTAL_JWT_ACCESSTOKENTTL=PT5M,
 * so nothing secret or environment-specific is compiled into the code.
 *
 *   issuer           "iss" claim written into every token and REQUIRED when reading one
 *   accessTokenTtl   lifetime of an access token (short: it cannot be recalled)
 *   refreshTokenTtl  lifetime of a refresh token (long: it is stored and can be revoked)
 *   clockSkew        tolerance for small clock differences between servers
 *   activeKeyId      the key that SIGNS new tokens (its id goes into the "kid" header)
 *   keys             key id -> Base64 secret; every listed key can VERIFY tokens
 */
@Validated
@ConfigurationProperties(prefix = "portal.jwt")
public record JwtProperties(
        @NotBlank String issuer,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl,
        @NotNull Duration clockSkew,
        @NotBlank String activeKeyId,
        @NotEmpty Map<String, String> keys) {
}
