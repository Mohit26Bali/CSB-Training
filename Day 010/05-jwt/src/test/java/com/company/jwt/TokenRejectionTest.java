package com.company.jwt;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/** Missing, malformed, expired and invalid tokens are all rejected with 401 and a reason. */
class TokenRejectionTest extends JwtTestSupport {

    private ResultActions callWith(String authorizationHeader) throws Exception {
        return mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, authorizationHeader));
    }

    private void expectRejected(ResultActions result, String reason) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(reason))
                .andExpect(header().string("WWW-Authenticate",
                        "Bearer error=\"invalid_token\", error_description=\"" + reason + "\""));
    }

    @Test
    void missingToken_gives401() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.message").value("Authentication is required to access this resource"));
    }

    @Test
    void wrongScheme_gives401() throws Exception {
        expectRejected(callWith("Basic aGFuYTpIYW5hQDEyMw=="), "Authorization header must be: Bearer <token>");
    }

    @Test
    void malformedToken_gives401() throws Exception {
        expectRejected(callWith(bearer("not-a-jwt")), "Token is malformed");
        expectRejected(callWith(bearer("")), "Token is malformed");
    }

    @Test
    void expiredToken_gives401() throws Exception {
        Instant twoHoursAgo = Instant.now().minusSeconds(7200);
        String expired = signedToken(properties.activeKeyId(), key(properties.activeKeyId()), properties.issuer(),
                "hana", List.of("ROLE_HR"), twoHoursAgo, twoHoursAgo.plusSeconds(900));
        expectRejected(callWith(bearer(expired)), "Token has expired");
    }

    /** Expired 10 seconds ago, within the 30 s clock skew: still accepted. */
    @Test
    void justExpired_withinTheClockSkew_isAccepted() throws Exception {
        Instant now = Instant.now();
        String token = signedToken(properties.activeKeyId(), key(properties.activeKeyId()), properties.issuer(),
                "hana", List.of("ROLE_HR"), now.minusSeconds(910), now.minusSeconds(10));
        callWith(bearer(token)).andExpect(status().isOk());
    }

    @Test
    void tokenSignedWithAnotherKey_gives401() throws Exception {
        Instant now = Instant.now();
        String foreign = signedToken(properties.activeKeyId(), Jwts.SIG.HS256.key().build(), properties.issuer(),
                "hana", List.of("ROLE_HR"), now, now.plusSeconds(600));
        expectRejected(callWith(bearer(foreign)), "Token signature is invalid");
    }

    /** The classic attack: edit the payload (give yourself USER_MANAGE), keep the signature. */
    @Test
    void tamperedPayload_gives401() throws Exception {
        String real = login("asha", "Asha@123").accessToken();
        String tampered = withPayload(real, decodePart(real, 1).replace("\"ROLE_USER\"", "\"ROLE_USER\",\"USER_MANAGE\""));

        expectRejected(callWith(bearer(tampered)), "Token signature is invalid");
        mockMvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(tampered)))
                .andExpect(status().isUnauthorized());
    }

    /** "alg":"none" - an UNSIGNED token. parseSignedClaims refuses it. */
    @Test
    void unsignedToken_gives401() throws Exception {
        String unsigned = Jwts.builder().issuer(properties.issuer()).subject("hana")
                .expiration(java.util.Date.from(Instant.now().plusSeconds(600)))
                .claim("authorities", List.of("USER_MANAGE")).compact();          // no signWith(...)
        expectRejected(callWith(bearer(unsigned)), "Token must be signed");
    }

    @Test
    void unknownKeyId_gives401() throws Exception {
        Instant now = Instant.now();
        String token = signedToken("k-unknown", Keys.hmacShaKeyFor(new byte[32]), properties.issuer(),
                "hana", List.of("ROLE_HR"), now, now.plusSeconds(600));
        expectRejected(callWith(bearer(token)), "Token was signed with an unknown key");
    }

    /** Correct key, but issued for another system ("iss"): refused. */
    @Test
    void otherIssuer_gives401() throws Exception {
        Instant now = Instant.now();
        String token = signedToken(properties.activeKeyId(), key(properties.activeKeyId()), "another-system",
                "hana", List.of("ROLE_HR"), now, now.plusSeconds(600));
        expectRejected(callWith(bearer(token)), "Token was not issued by this server");
    }

    /** An invalid token is refused even on a public endpoint: a broken client should find out. */
    @Test
    void invalidToken_onAPublicEndpoint_isStillRefused() throws Exception {
        mockMvc.perform(get("/api/public/hello").header(HttpHeaders.AUTHORIZATION, bearer("not-a-jwt")))
                .andExpect(status().isUnauthorized());
    }
}
