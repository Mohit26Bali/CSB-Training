package com.csb.banking.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.csb.banking.dto.AuthResponse;
import com.csb.banking.dto.LoginRequest;
import com.csb.banking.dto.RegisterRequest;
import com.csb.banking.dto.UserResponse;
import com.csb.banking.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * {@code /api/auth}: login, customer registration and current user.
 *
 * <p>login and register are public (see {@code config/SecurityConfig}); {@code /me} needs a valid token. All logic is
 * in {@code service/AuthService}; request / response shapes are {@code dto/LoginRequest}, {@code dto/RegisterRequest},
 * {@code dto/AuthResponse} and {@code dto/UserResponse}.
 */
@Tag(name = "Authentication", description = "Register, log in and identify the current user")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Log in", description = "Returns a JWT. Send it as `Authorization: Bearer <token>` on every other call.")
    @SecurityRequirements
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @Operation(summary = "Register as a customer", description = "Creates the login and the customer profile, and signs the new customer in.")
    @SecurityRequirements
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @Operation(summary = "Current user")
    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return authService.currentUser(authentication.getName());
    }
}
