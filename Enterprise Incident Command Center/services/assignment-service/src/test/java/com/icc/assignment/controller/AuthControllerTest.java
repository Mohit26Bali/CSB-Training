package com.icc.assignment.controller;

import com.icc.assignment.dto.LoginRequest;
import com.icc.assignment.dto.LoginResponse;
import com.icc.assignment.dto.UserResponse;
import com.icc.assignment.exception.InvalidCredentialsException;
import com.icc.assignment.service.AuthService;
import com.icc.common.jwt.Role;
import com.icc.common.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void login_returnsTokenAndUser_withoutAnyPasswordHash() throws Exception {
        UserResponse user = new UserResponse(2L, "sd.engineer", "Sam", "sam@icc.local", Role.SUPPORT_ENGINEER, 1L);
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(new LoginResponse("the-token", "Bearer", 3600, user));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"sd.engineer\",\"password\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("the-token"))
                .andExpect(jsonPath("$.user.role").value("SUPPORT_ENGINEER"))
                .andExpect(content().string(not(containsString("password"))));
    }

    @Test
    void login_withABlankUsername_is400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"pw\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void login_withWrongCredentials_is401() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"x\",\"password\":\"y\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }
}
