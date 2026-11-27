package com.springlab.lab.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springlab.entity.Role;
import com.springlab.entity.User;
import com.springlab.security.AppUserDetailsService;
import com.springlab.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the lesson's claim about WHERE the authentication components are used: the AuthenticationManager /
 * UserDetailsService / PasswordEncoder pipeline serves the username-and-password LOGIN only; a bearer token request is
 * authenticated by the JwtDecoder and the converter and never touches them.
 */
class LoginVersusBearerIntegrationTest extends AbstractIntegrationTest {

    @MockitoSpyBean
    private AppUserDetailsService userDetailsService;
    @MockitoSpyBean
    private PasswordEncoder encoder;
    @Autowired
    private ObjectMapper objectMapper;

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    @Test
    void loginUsesTheUserDetailsServiceAndThePasswordEncoder() throws Exception {
        createUser("sam", Role.STUDENT);
        Mockito.clearInvocations(userDetailsService, encoder);

        login("sam");

        verify(userDetailsService, atLeastOnce()).loadUserByUsername("sam");
        verify(encoder, atLeastOnce()).matches(any(), any());
    }

    @Test
    void bearerAuthenticatedRequestsNeverUseEitherOfThem() throws Exception {
        User sam = createUser("sam", Role.STUDENT);
        String token = login("sam");
        String labToken = objectMapper.readTree(mockMvc.perform(post("/api/lab/jwt/issue")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"variant\":\"VALID\"}")).andReturn().getResponse().getContentAsString()).get("token").asText();
        Mockito.clearInvocations(userDetailsService, encoder);

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mockMvc.perform(get("/api/topics").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mockMvc.perform(get("/api/lab/secure/whoami").header("Authorization", "Bearer " + labToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());

        verify(userDetailsService, never()).loadUserByUsername(any());
        verify(encoder, never()).matches(any(), any());
        verify(encoder, never()).encode(any());
        org.junit.jupiter.api.Assertions.assertNotNull(sam.getId());
    }

    @Test
    void aRejectedTokenDoesNotFallBackToThePasswordPipelineEither() throws Exception {
        createUser("sam", Role.STUDENT);
        Mockito.clearInvocations(userDetailsService, encoder);

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not.a.jwt")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());

        verify(userDetailsService, never()).loadUserByUsername(any());
        verify(encoder, never()).matches(any(), any());
    }
}
