package com.springlab.config;

import com.springlab.entity.Role;
import com.springlab.repository.UserRepository;
import com.springlab.security.DatabaseBackedJwtAuthenticationConverter;
import com.springlab.security.UserAuthInfo;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Pure unit tests: no Spring context, no database. */
class SecurityUnitTests {

    private static Jwt jwtWith(Map<String, Object> extraClaims) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "HS256").subject("alice")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        extraClaims.forEach(builder::claim);
        return builder.build();
    }

    private static DatabaseBackedJwtAuthenticationConverter converterFor(UserAuthInfo user) {
        UserRepository repository = mock(UserRepository.class);
        when(repository.findAuthInfoByUsername("alice")).thenReturn(Optional.ofNullable(user));
        return new DatabaseBackedJwtAuthenticationConverter(repository);
    }

    @Test
    void databaseRoleIsConvertedToRolePrefixedAuthority() {
        var converter = converterFor(new UserAuthInfo("alice", Role.TEACHER, true));

        var authentication = converter.convert(jwtWith(Map.of()));

        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_TEACHER");
        assertThat(authentication.getName()).isEqualTo("alice");
    }

    @Test
    void roleAndScopeClaimsInTheTokenAreNeverTrusted() {
        var converter = converterFor(new UserAuthInfo("alice", Role.STUDENT, true));

        var authentication = converter.convert(jwtWith(Map.of("role", "ADMIN", "scope", "read write")));

        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_STUDENT");
    }

    @Test
    void disabledUserIsRejected() {
        var converter = converterFor(new UserAuthInfo("alice", Role.STUDENT, false));

        assertThatThrownBy(() -> converter.convert(jwtWith(Map.of())))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void unknownUserIsRejected() {
        var converter = converterFor(null);

        assertThatThrownBy(() -> converter.convert(jwtWith(Map.of())))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void signingKeyShorterThan256BitsIsRejected() {
        JwtConfig config = new JwtConfig();
        JwtProperties tooShort = new JwtProperties("only-31-bytes-long-secret-key!!", "springlab", Duration.ofMinutes(30));

        assertThatThrownBy(() -> config.jwtSecretKey(tooShort))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes")
                .hasMessageNotContaining("only-31-bytes");
    }

    @Test
    void unresolvedPlaceholderIsReportedAsMissingSecret() {
        JwtProperties unresolved = new JwtProperties("${JWT_SECRET}", "springlab", Duration.ofMinutes(30));

        assertThatThrownBy(() -> new JwtConfig().jwtSecretKey(unresolved))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET environment variable is not set");
    }

    @Test
    void signingKeyOf256BitsOrMoreIsAccepted() {
        JwtConfig config = new JwtConfig();
        JwtProperties ok = new JwtProperties("0123456789abcdef0123456789abcdef", "springlab", Duration.ofMinutes(30));

        assertThat(config.jwtSecretKey(ok).getAlgorithm()).isEqualTo("HmacSHA256");
    }

    @Test
    void propertiesToStringDoesNotLeakSecret() {
        JwtProperties props = new JwtProperties("super-secret-value-super-secret-value", "springlab", Duration.ofMinutes(30));

        assertThat(props.toString()).doesNotContain("super-secret-value");
    }
}
