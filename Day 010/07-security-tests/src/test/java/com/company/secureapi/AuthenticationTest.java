package com.company.secureapi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** Public vs protected, invalid credentials, invalid / expired / tampered JWT. */
class AuthenticationTest extends SecurityTestSupport {

    @Test
    void publicEndpoint_withoutAuthentication_is200() throws Exception {
        mockMvc.perform(get("/api/public/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void protectedEndpoint_withoutAuthentication_is401() throws Exception {
        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void invalidCredentials_are401_andGiveNoToken() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"asha\",\"password\":\"Wrong@123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_BAD_CREDENTIALS"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody\",\"password\":\"Asha@123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validCredentials_giveAWorkingToken() throws Exception {
        mockMvc.perform(get("/api/employees").header(HttpHeaders.AUTHORIZATION, bearer("asha")))
                .andExpect(status().isOk());
    }

    @Test
    void invalidJwt_is401() throws Exception {
        mockMvc.perform(get("/api/employees").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_TOKEN"));
    }

    @Test
    void expiredJwt_is401() throws Exception {
        mockMvc.perform(get("/api/employees").header(HttpHeaders.AUTHORIZATION, expiredBearer("hana", "HR")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Token has expired"));
    }

    /** A real token with one character of the payload changed: the signature no longer matches. */
    @Test
    void tamperedJwt_is401() throws Exception {
        String token = bearer("asha");
        int middle = token.indexOf('.') + 5;
        String tampered = token.substring(0, middle) + (token.charAt(middle) == 'A' ? 'B' : 'A') + token.substring(middle + 1);
        mockMvc.perform(get("/api/employees").header(HttpHeaders.AUTHORIZATION, tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_TOKEN"));
    }
}
