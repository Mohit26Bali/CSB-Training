package com.company.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;

/** login -> token -> protected endpoint, and what is inside the token. */
class LoginAndAccessTest extends JwtTestSupport {

    private final ObjectMapper json = new ObjectMapper();

    private ResultActions loginRequest(String body) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void login_returnsABearerTokenPair() throws Exception {
        loginRequest("{\"username\":\"hana\",\"password\":\"Hana@123\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))                   // PT15M
                .andExpect(jsonPath("$.refreshExpiresIn").value(604800))         // P7D
                .andExpect(jsonPath("$.accessToken").value(Matchers.matchesPattern("^[\\w-]+\\.[\\w-]+\\.[\\w-]+$")))
                .andExpect(jsonPath("$.refreshToken").value(Matchers.matchesPattern("^[\\w-]{43}$")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void theTokenContains_subject_issuedAt_expiry_authorities_andTheKeyId() throws Exception {
        String token = login("hana", "Hana@123").accessToken();

        Map<String, Object> header = json.readValue(decodePart(token, 0), Map.class);
        Map<String, Object> payload = json.readValue(decodePart(token, 1), Map.class);

        assertThat(header).containsEntry("alg", "HS256").containsEntry("kid", "test-new");
        assertThat(payload).containsEntry("sub", "hana").containsEntry("iss", "hr-portal").containsKey("jti");
        assertThat((List<String>) payload.get("authorities")).containsExactly("EMPLOYEE_READ", "EMPLOYEE_WRITE", "ROLE_HR");
        assertThat(((Number) payload.get("exp")).longValue() - ((Number) payload.get("iat")).longValue()).isEqualTo(900);
        // only ENCODED, not encrypted: anyone can read it, so no secrets inside
        assertThat(decodePart(token, 1)).doesNotContain("Hana@123").doesNotContain("$2a$");
    }

    @Test
    void tokenToProtectedEndpoint_works_andTheAuthoritiesComeFromTheToken() throws Exception {
        String token = login("manoj", "Manoj@123").accessToken();

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("manoj"))
                .andExpect(jsonPath("$.authorities").value(Matchers.contains("EMPLOYEE_READ", "ROLE_MANAGER", "ROLE_USER")));
        mockMvc.perform(get("/api/directory").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden())                                // valid token, missing USER_MANAGE
                .andExpect(jsonPath("$.message").value("You do not have permission to access this resource"));
    }

    /**
     * The server TRUSTS the signed claims without asking the database: a token
     * signed with the real key that says "asha has USER_MANAGE" opens the admin
     * API, although asha has no such permission in MySQL. This is why the signing
     * key must stay secret - whoever has it can mint any identity.
     */
    @Test
    void theServerTrustsTheSignedClaims_notTheDatabase() throws Exception {
        String forgedWithTheRealKey = validToken("asha", List.of("USER_MANAGE"));

        mockMvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(forgedWithTheRealKey)))
                .andExpect(status().isOk());
    }

    @Test
    void wrongPassword_unknownUser_andDisabledUser_get401_andNoToken() throws Exception {
        loginRequest("{\"username\":\"hana\",\"password\":\"Wrong@123\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
        loginRequest("{\"username\":\"nobody\",\"password\":\"Hana@123\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
        loginRequest("{\"username\":\"vikram\",\"password\":\"Vikram@123\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Account is disabled"));
    }

    @Test
    void missingFields_give400() throws Exception {
        loginRequest("{\"username\":\"hana\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("password must not be blank"));
    }

    /** STATELESS: no HTTP session and no session cookie, at login or afterwards. */
    @Test
    void noSessionIsEverCreated() throws Exception {
        MvcResult loginResult = loginRequest("{\"username\":\"hana\",\"password\":\"Hana@123\"}").andReturn();
        String token = json.readTree(loginResult.getResponse().getContentAsString()).get("accessToken").asText();
        MvcResult meResult = mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(token))).andReturn();

        for (MvcResult result : List.of(loginResult, meResult)) {
            assertThat(result.getRequest().getSession(false)).isNull();
            assertThat(result.getResponse().getHeader("Set-Cookie")).isNull();
        }
    }

    @Test
    void publicEndpoint_needsNoToken() throws Exception {
        mockMvc.perform(get("/api/public/hello")).andExpect(status().isOk());
    }
}
