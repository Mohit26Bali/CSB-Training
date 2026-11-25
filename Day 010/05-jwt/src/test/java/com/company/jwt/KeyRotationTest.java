package com.company.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.company.jwt.config.JwtProperties;
import com.company.jwt.security.JwtKeyRing;

import io.jsonwebtoken.security.WeakKeyException;

/** Several keys by "kid": new tokens use the active key, older keys still verify. */
class KeyRotationTest extends JwtTestSupport {

    @Test
    void newTokens_areSignedWithTheActiveKey() throws Exception {
        String token = login("asha", "Asha@123").accessToken();
        assertThat(decodePart(token, 0)).contains("\"kid\":\"test-new\"");
    }

    /** A token signed before the rotation, with the OLD key, is still accepted. */
    @Test
    void tokenFromTheOldKey_isStillAccepted() throws Exception {
        Instant now = Instant.now();
        String oldToken = signedToken("test-old", key("test-old"), properties.issuer(),
                "asha", List.of("EMPLOYEE_READ", "ROLE_USER"), now, now.plusSeconds(600));

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(oldToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("asha"));
    }

    /** The kid decides the key: an old-key signature under the NEW key's id fails. */
    @Test
    void theKidMustMatchTheKeyThatSigned() throws Exception {
        Instant now = Instant.now();
        String mislabelled = signedToken("test-new", key("test-old"), properties.issuer(),
                "asha", List.of("ROLE_USER"), now, now.plusSeconds(600));

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(mislabelled)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Token signature is invalid"));
    }

    @Test
    void startup_failsIfTheActiveKeyIsNotConfigured() {
        JwtProperties wrong = new JwtProperties("hr-portal", Duration.ofMinutes(15), Duration.ofDays(7),
                Duration.ofSeconds(30), "missing", Map.of("k1", properties.keys().get("test-new")));
        assertThatThrownBy(() -> new JwtKeyRing(wrong))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'missing' is not one of the configured keys");
    }

    /** HS256 needs at least 256 bits: a 128-bit key is refused at startup. */
    @Test
    void startup_failsForAKeyShorterThan256Bits() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        JwtProperties weak = new JwtProperties("hr-portal", Duration.ofMinutes(15), Duration.ofDays(7),
                Duration.ofSeconds(30), "k1", Map.of("k1", shortKey));
        assertThatThrownBy(() -> new JwtKeyRing(weak)).isInstanceOf(WeakKeyException.class);
    }
}
