package com.csb.cinescope.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.csb.cinescope.dto.AuthResponse;
import com.csb.cinescope.dto.LoginRequest;
import com.csb.cinescope.dto.RegisterRequest;
import com.csb.cinescope.dto.UserResponse;
import com.csb.cinescope.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * {@code /api/auth}: register, log in and "who am I". Register and login are public ({@code config/SecurityConfig});
 * {@code /me} needs a valid token. All logic is in {@code service/AuthService}; the request / response shapes are
 * {@code dto/RegisterRequest}, {@code dto/LoginRequest}, {@code dto/AuthResponse} and {@code dto/UserResponse}.
 */
@Tag(name = "Accounts", description = "Create an account, log in and identify the current user")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Create an account", description = "Returns a token, so the new user is logged in straight away.")
    @SecurityRequirements
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @Operation(summary = "Log in", description = "Returns a token. Send it as `Authorization: Bearer <token>` on protected calls.")
    @SecurityRequirements
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @Operation(summary = "Current user")
    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return authService.currentUser(authentication.getName());
    }
}
