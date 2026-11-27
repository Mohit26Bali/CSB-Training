package com.hackathon.enrollment.service;

import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.enrollment.dto.AuthResponse;
import com.hackathon.enrollment.dto.LoginRequest;
import com.hackathon.enrollment.dto.RegisterRequest;
import com.hackathon.enrollment.dto.UserResponse;
import com.hackathon.enrollment.entity.Role;
import com.hackathon.enrollment.entity.User;
import com.hackathon.enrollment.exception.DuplicateUsernameException;
import com.hackathon.enrollment.exception.ResourceNotFoundException;
import com.hackathon.enrollment.repo.UserRepository;
import com.hackathon.enrollment.security.JwtService;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;

    public AuthService(AuthenticationManager authenticationManager, UserRepository userRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       LoginAttemptService loginAttemptService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
    }

    public AuthResponse login(LoginRequest request) {
        String username = request.getUsername().trim();
        loginAttemptService.checkNotLocked(username);
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, request.getPassword()));
        } catch (AuthenticationException ex) {
            loginAttemptService.recordFailure(username);
            // Same message for unknown user, wrong password and disabled account: no user enumeration.
            throw new BadCredentialsException("Invalid username or password");
        }
        loginAttemptService.recordSuccess(username);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));
        return toAuthResponse(user);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.getUsername().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateUsernameException("Username '" + username + "' is already taken");
        }
        User user = new User(username, passwordEncoder.encode(request.getPassword()),
                request.getFullname().trim(), Role.EMPLOYEE);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateUsernameException("Username '" + username + "' is already taken");
        }
        return toAuthResponse(user);
    }

    public UserResponse currentUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        return new UserResponse(user.getUsername(), user.getFullname(), user.getRole().name());
    }

    private AuthResponse toAuthResponse(User user) {
        String role = user.getRole().name();
        return new AuthResponse(jwtService.generateToken(user.getUsername(), role),
                jwtService.getExpiresInSeconds(), user.getUsername(), user.getFullname(), role);
    }
}
