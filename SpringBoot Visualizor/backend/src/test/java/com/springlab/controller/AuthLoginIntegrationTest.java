package com.springlab.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springlab.config.JwtProperties;
import com.springlab.entity.Role;
import com.springlab.entity.User;
import com.springlab.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthLoginIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JwtDecoder jwtDecoder;
    @Autowired
    private JwtProperties jwtProperties;
    @Autowired
    private ObjectMapper objectMapper;

    private org.springframework.test.web.servlet.ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"%s","password":"%s"}""".formatted(username, password)));
    }

    @Test
    void issuesSignedTokenWithExpectedClaims() throws Exception {
        createUser("teacher1", Role.TEACHER);

        MvcResult result = login("teacher1", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(jwtProperties.accessTokenTtl().toSeconds()))
                .andExpect(jsonPath("$.accessToken").isString())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        // Decoding through the real decoder also proves the signature, expiry and issuer checks pass.
        Jwt jwt = jwtDecoder.decode(json.get("accessToken").asText());

        assertThat(jwt.getSubject()).isEqualTo("teacher1");
        // Read as a string: Jwt#getIssuer() would try to parse "springlab" as a URL (iss is StringOrURI per RFC 7519).
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("springlab");
        assertThat(jwt.<String>getClaim("role")).isEqualTo("TEACHER");
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(jwtProperties.accessTokenTtl());
        assertThat(jwtProperties.accessTokenTtl()).isBetween(Duration.ofMinutes(15), Duration.ofMinutes(30));
        assertThat(jwt.getHeaders().get("alg")).isEqualTo("HS256");
        // The token must not carry credentials or contact data.
        assertThat(jwt.getClaims()).doesNotContainKeys("password", "passwordHash", "email");
    }

    @Test
    void rejectsWrongPasswordWithGenericMessage() throws Exception {
        createUser("alice", Role.STUDENT);

        login("alice", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid username or password"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void unknownUserIsIndistinguishableFromWrongPassword() throws Exception {
        login("ghost", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));
    }

    @Test
    void rejectsDisabledAccount() throws Exception {
        User user = createUser("alice", Role.STUDENT);
        user.setEnabled(false);
        userRepository.saveAndFlush(user);

        login("alice", PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsBlankCredentialsWithValidationError() throws Exception {
        login("", "").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void loginEndpointStaysPublicButSetsBearerChallengeOnFailure() throws Exception {
        login("ghost", PASSWORD).andExpect(header().string("WWW-Authenticate", "Bearer"));
    }
}
