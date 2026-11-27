package com.springlab.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Symmetric (HS256) JWT setup. The same secret signs tokens (encoder) and verifies them (decoder).
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    /** RFC 7518 section 3.2: an HS256 key must be at least as long as the hash output (256 bits). */
    static final int MIN_SECRET_BYTES = 32;

    @Bean
    public SecretKey jwtSecretKey(JwtProperties properties) {
        // Boot keeps an unresolvable "${JWT_SECRET}" placeholder as literal text instead of failing.
        if (properties.secret().startsWith("${")) {
            throw new IllegalStateException("JWT_SECRET environment variable is not set");
        }
        byte[] keyBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "springlab.jwt.secret (env JWT_SECRET) must be at least " + MIN_SECRET_BYTES
                            + " bytes (256 bits) for HS256, but is " + keyBytes.length + " bytes");
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey, JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // Default validators (exp / nbf with 60s clock skew) plus issuer check.
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }
}
