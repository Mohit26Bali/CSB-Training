package com.cartnest.service;

import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cartnest.dto.AuthResponse;
import com.cartnest.dto.LoginRequest;
import com.cartnest.dto.RegisterRequest;
import com.cartnest.dto.UserResponse;
import com.cartnest.entity.Role;
import com.cartnest.entity.User;
import com.cartnest.exception.ConflictException;
import com.cartnest.exception.ResourceNotFoundException;
import com.cartnest.repository.UserRepository;
import com.cartnest.security.JwtService;
import com.cartnest.security.SecurityUser;

@Service
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttempts;

    public AuthService(AuthenticationManager authenticationManager, UserRepository userRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService, LoginAttemptService loginAttempts) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttempts = loginAttempts;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalise(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        User user = new User(request.fullName().trim(), email, passwordEncoder.encode(request.password()),
                Role.CUSTOMER);
        user.setPhone(request.phone());
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("An account with this email already exists");
        }
        return tokenFor(user);
    }

    public AuthResponse login(LoginRequest request) {
        String email = normalise(request.email());
        loginAttempts.checkNotLocked(email);
        SecurityUser principal;
        try {
            principal = (SecurityUser) authenticationManager
                    .authenticate(new UsernamePasswordAuthenticationToken(email, request.password()))
                    .getPrincipal();
        } catch (AuthenticationException ex) {
            loginAttempts.recordFailure(email);

            throw new BadCredentialsException("Incorrect email or password");
        }
        loginAttempts.recordSuccess(email);
        return tokenFor(userRepository.findById(principal.getId()).orElseThrow());
    }

    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        return toResponse(userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found")));
    }

    static UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(), user.getRole());
    }

    private AuthResponse tokenFor(User user) {
        JwtService.IssuedToken token = jwtService.issue(new SecurityUser(user));
        return new AuthResponse(token.token(), token.expiresAt(), toResponse(user));
    }

    private static String normalise(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
