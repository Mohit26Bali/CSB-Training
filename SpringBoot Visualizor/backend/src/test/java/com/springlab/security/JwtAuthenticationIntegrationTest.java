package com.springlab.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.springlab.entity.Role;
import com.springlab.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.ResultActions;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JwtAuthenticationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JwtEncoder jwtEncoder; // the application's real encoder (valid signing key)

    private String token(JwtEncoder encoder, String subject, String role, String issuer, Instant issuedAt,
                         Instant expiresAt) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer).subject(subject).issuedAt(issuedAt).expiresAt(expiresAt);
        if (role != null) {
            claims.claim("role", role);
        }
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
    }

    private String validToken(String subject, Role role) {
        Instant now = Instant.now();
        return token(jwtEncoder, subject, role.name(), "springlab", now, now.plus(10, ChronoUnit.MINUTES));
    }

    private ResultActions getWithToken(String path, String token) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + token));
    }

    private void expectProblem(ResultActions result, int status) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status));
    }

    // ---- /api/auth/me ----

    @Test
    void meReturnsCurrentUserForValidToken() throws Exception {
        var user = createUser("alice", Role.STUDENT);

        getWithToken("/api/auth/me", validToken("alice", Role.STUDENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void meReturns401WithoutToken() throws Exception {
        ResultActions result = mockMvc.perform(get("/api/auth/me"));
        expectProblem(result, 401);
        result.andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Bearer")));
    }

    @Test
    void meReturns401ForGarbageToken() throws Exception {
        expectProblem(getWithToken("/api/auth/me", "not.a.jwt"), 401);
    }

    @Test
    void meReturns401ForNonBearerScheme() throws Exception {
        expectProblem(mockMvc.perform(get("/api/auth/me").header("Authorization", "Basic YWxpY2U6cHc=")), 401);
    }

    @Test
    void meReturns401ForExpiredToken() throws Exception {
        createUser("alice", Role.STUDENT);
        // Expired well beyond the decoder's default 60s clock skew.
        Instant now = Instant.now();
        String expired = token(jwtEncoder, "alice", "STUDENT", "springlab",
                now.minus(2, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS));

        expectProblem(getWithToken("/api/auth/me", expired), 401);
    }

    @Test
    void meReturns401ForTokenSignedWithDifferentKey() throws Exception {
        createUser("alice", Role.STUDENT);
        JwtEncoder attacker = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec("attacker-key-attacker-key-attacker-key!".getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        Instant now = Instant.now();
        String forged = token(attacker, "alice", "ADMIN", "springlab", now, now.plus(10, ChronoUnit.MINUTES));

        expectProblem(getWithToken("/api/auth/me", forged), 401);
    }

    @Test
    void meReturns401ForTamperedPayload() throws Exception {
        createUser("alice", Role.STUDENT);
        String[] parts = validToken("alice", Role.STUDENT).split("\\.");
        // Swap in a payload claiming ADMIN but keep the original signature.
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"alice\",\"iss\":\"springlab\",\"role\":\"ADMIN\",\"exp\":" + Instant.now().plusSeconds(600).getEpochSecond() + "}")
                        .getBytes(StandardCharsets.UTF_8));
        String tampered = parts[0] + "." + forgedPayload + "." + parts[2];

        expectProblem(getWithToken("/api/auth/me", tampered), 401);
    }

    @Test
    void meReturns401ForUnsignedAlgNoneToken() throws Exception {
        java.util.Base64.Encoder enc = java.util.Base64.getUrlEncoder().withoutPadding();
        String header = enc.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = enc.encodeToString(("{\"sub\":\"alice\",\"iss\":\"springlab\",\"role\":\"ADMIN\",\"exp\":"
                + Instant.now().plusSeconds(600).getEpochSecond() + "}").getBytes(StandardCharsets.UTF_8));

        expectProblem(getWithToken("/api/auth/me", header + "." + payload + "."), 401);
    }

    @Test
    void meReturns401ForWrongIssuer() throws Exception {
        createUser("alice", Role.STUDENT);
        Instant now = Instant.now();
        String token = token(jwtEncoder, "alice", "STUDENT", "someone-else", now, now.plus(10, ChronoUnit.MINUTES));

        expectProblem(getWithToken("/api/auth/me", token), 401);
    }

    @Test
    void meReturns401WhenTokenSubjectNoLongerExists() throws Exception {
        expectProblem(getWithToken("/api/auth/me", validToken("deleted-user", Role.STUDENT)), 401);
    }

    // ---- 401 vs 403 and role -> authority mapping (method security via test-only RoleProbeController) ----

    @Test
    void protectedEndpointWithoutTokenIs401NotRedirectNorSession() throws Exception {
        expectProblem(mockMvc.perform(get("/api/test/any")), 401);
        expectProblem(mockMvc.perform(get("/api/test/admin")), 401);
    }

    @Test
    void authenticatedButNotAuthorizedIs403() throws Exception {
        createUser("alice", Role.STUDENT);
        expectProblem(getWithToken("/api/test/admin", validToken("alice", Role.STUDENT)), 403);
        expectProblem(getWithToken("/api/test/teacher", validToken("alice", Role.STUDENT)), 403);
    }

    @Test
    void databaseRoleBecomesRolePrefixedAuthority() throws Exception {
        createUser("root", Role.ADMIN);
        createUser("tina", Role.TEACHER);
        createUser("alice", Role.STUDENT);

        getWithToken("/api/test/admin", validToken("root", Role.ADMIN)).andExpect(status().isOk());
        getWithToken("/api/test/teacher", validToken("root", Role.ADMIN)).andExpect(status().isOk());
        getWithToken("/api/test/teacher", validToken("tina", Role.TEACHER)).andExpect(status().isOk());
        getWithToken("/api/test/any", validToken("alice", Role.STUDENT)).andExpect(status().isOk());
        // Teacher is not an admin.
        expectProblem(getWithToken("/api/test/admin", validToken("tina", Role.TEACHER)), 403);
    }

    @Test
    void roleClaimInTokenIsIgnoredAndDatabaseRoleWins() throws Exception {
        createUser("alice", Role.STUDENT);
        createUser("tina", Role.TEACHER);

        // A validly signed token that CLAIMS admin does not make a student an admin.
        expectProblem(getWithToken("/api/test/admin", validToken("alice", Role.ADMIN)), 403);
        // A token with no role claim at all still gets the teacher's database role.
        Instant now = Instant.now();
        String noRole = token(jwtEncoder, "tina", null, "springlab", now, now.plus(10, ChronoUnit.MINUTES));
        getWithToken("/api/test/teacher", noRole).andExpect(status().isOk());
    }

    @Test
    void roleChangeTakesEffectOnNextRequestWithSameToken() throws Exception {
        var user = createUser("alice", Role.STUDENT);
        String token = validToken("alice", Role.STUDENT);
        expectProblem(getWithToken("/api/test/teacher", token), 403);

        user.setRole(Role.TEACHER);
        userRepository.saveAndFlush(user);

        getWithToken("/api/test/teacher", token).andExpect(status().isOk());
    }

    @Test
    void disablingAccountRejectsExistingTokenImmediately() throws Exception {
        var user = createUser("alice", Role.STUDENT);
        String token = validToken("alice", Role.STUDENT);
        getWithToken("/api/auth/me", token).andExpect(status().isOk());

        user.setEnabled(false);
        userRepository.saveAndFlush(user);

        expectProblem(getWithToken("/api/auth/me", token), 401);
        expectProblem(getWithToken("/api/test/any", token), 401);
    }

    @Test
    void deletedAccountRejectsExistingTokenOnAnyEndpoint() throws Exception {
        expectProblem(getWithToken("/api/test/any", validToken("ghost", Role.STUDENT)), 401);
    }

    @Test
    void unexpectedExceptionsBecomeGeneric500WithoutLeakingDetails() throws Exception {
        createUser("alice", Role.STUDENT);
        getWithToken("/api/test/boom", validToken("alice", Role.STUDENT))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret internal detail"))));
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
