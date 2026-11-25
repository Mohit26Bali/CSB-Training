package com.company.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

/** Refresh-token rotation, reuse detection, logout, and what revocation can and cannot do. */
class RefreshAndRevocationTest extends JwtTestSupport {

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"));
    }

    private Tokens tokensOf(ResultActions result) throws Exception {
        String json = result.andReturn().getResponse().getContentAsString();
        return new Tokens(JsonPath.read(json, "$.accessToken"), JsonPath.read(json, "$.refreshToken"));
    }

    private ResultActions me(String accessToken) throws Exception {
        return mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)));
    }

    private String adminToken() throws Exception {
        return login("admin", "Admin@123").accessToken();
    }

    @Test
    void refresh_givesANewPair_andTheNewAccessTokenWorks() throws Exception {
        String user = createUser();
        Tokens first = login(user, "Asha@123");

        Tokens second = tokensOf(refresh(first.refreshToken()).andExpect(status().isOk()));

        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());     // rotated
        assertThat(second.accessToken()).isNotEqualTo(first.accessToken());
        me(second.accessToken()).andExpect(status().isOk());
    }

    /** A rotated token used again = it was copied: the whole session is revoked, even the newest token. */
    @Test
    void reusingARotatedRefreshToken_revokesTheWholeSession() throws Exception {
        String user = createUser();
        Tokens first = login(user, "Asha@123");
        Tokens second = tokensOf(refresh(first.refreshToken()));

        refresh(first.refreshToken())                                              // the "stolen" old one
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh token reuse detected; please log in again"));
        refresh(second.refreshToken())                                             // the real user's newest one
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh token has been revoked"));

        assertThat(jdbc.queryForList("SELECT revoked_reason FROM refresh_tokens WHERE user_id = ? ORDER BY id",
                String.class, idOf(user))).containsExactly("ROTATED", "REUSE_DETECTED");
    }

    @Test
    void madeUpRefreshToken_gives401() throws Exception {
        refresh("not-a-real-refresh-token")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh token is invalid"));
    }

    @Test
    void refreshTokensAreStoredOnlyAsSha256Hashes() throws Exception {
        String user = createUser();
        Tokens tokens = login(user, "Asha@123");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE token_hash = ?",
                Integer.class, tokens.refreshToken())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE token_hash = SHA2(?, 256)",
                Integer.class, tokens.refreshToken())).isEqualTo(1);
    }

    /** Logout: this access token (deny-listed by jti) and this refresh token stop working. */
    @Test
    void logout_revokesTheAccessAndTheRefreshToken_butNotOtherSessions() throws Exception {
        String user = createUser();
        Tokens laptop = login(user, "Asha@123");
        Tokens phone = login(user, "Asha@123");

        mockMvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(laptop.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + laptop.refreshToken() + "\"}"))
                .andExpect(status().isNoContent());

        me(laptop.accessToken())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Token has been revoked"));
        refresh(laptop.refreshToken()).andExpect(status().isUnauthorized());

        me(phone.accessToken()).andExpect(status().isOk());                       // the other device is unaffected
        refresh(phone.refreshToken()).andExpect(status().isOk());
    }

    @Test
    void logout_needsAValidAccessToken() throws Exception {
        mockMvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
    }

    /**
     * The limit of stateless tokens: after the admin disables the account, the
     * access token issued BEFORE keeps working until it expires (max 15 minutes).
     * What stops at once is getting a new one: refresh and login are refused.
     */
    @Test
    void disabledUser_keepsTheCurrentAccessToken_butCannotRefreshOrLogIn() throws Exception {
        String user = createUser();
        Tokens tokens = login(user, "Asha@123");

        mockMvc.perform(patch("/api/admin/users/{id}/enabled", idOf(user))
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk());

        me(tokens.accessToken()).andExpect(status().isOk());                      // still valid: stateless
        refresh(tokens.refreshToken()).andExpect(status().isUnauthorized());      // revoked when disabled
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"Asha@123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Account is disabled"));
    }

    /** Role changes reach the token only at the next refresh, because a token is a snapshot. */
    @Test
    void roleChange_appearsAfterTheNextRefresh() throws Exception {
        String user = createUser();
        Tokens before = login(user, "Asha@123");
        jdbc.update("INSERT INTO user_roles (user_id, role_id) SELECT ?, id FROM roles WHERE name = 'HR'", idOf(user));

        me(before.accessToken())
                .andExpect(jsonPath("$.authorities").value(Matchers.contains("EMPLOYEE_READ", "ROLE_USER")));

        Tokens after = tokensOf(refresh(before.refreshToken()));
        me(after.accessToken())
                .andExpect(jsonPath("$.authorities").value(Matchers.contains(
                        "EMPLOYEE_READ", "EMPLOYEE_WRITE", "ROLE_HR", "ROLE_USER")));
    }

    @Test
    void adminRevokeTokens_endsEverySessionOfTheUser() throws Exception {
        String user = createUser();
        Tokens one = login(user, "Asha@123");
        Tokens two = login(user, "Asha@123");

        mockMvc.perform(post("/api/admin/users/{id}/revoke-tokens", idOf(user))
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revokedRefreshTokens").value(2));

        refresh(one.refreshToken()).andExpect(status().isUnauthorized());
        refresh(two.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void anAdminCannotDisableTheirOwnAccount() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{id}/enabled", idOf("admin"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isConflict());
    }
}
