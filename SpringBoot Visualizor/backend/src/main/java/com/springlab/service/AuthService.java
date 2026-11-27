package com.springlab.service;

import com.springlab.dto.auth.AuthResponse;
import com.springlab.dto.auth.LoginRequest;
import com.springlab.dto.auth.RegisterRequest;
import com.springlab.dto.user.UserResponse;
import com.springlab.entity.Role;
import com.springlab.entity.User;
import com.springlab.exception.DuplicateResourceException;
import com.springlab.mapper.UserMapper;
import com.springlab.repository.UserRepository;
import com.springlab.security.JwtService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String username = request.username();
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByUsername(username)) {
            throw new DuplicateResourceException("Username is already taken");
        }
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email is already registered");
        }

        // The role is fixed here, never read from the request.
        User user = new User(username, email, passwordEncoder.encode(request.password()), Role.STUDENT);
        try {
            return UserMapper.toResponse(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException e) {
            // Two concurrent registrations can both pass the exists* checks; the unique constraints decide.
            throw new DuplicateResourceException("Username or email is already in use");
        }
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new BadCredentialsException("User not found"));
        JwtService.AccessToken token = jwtService.issueFor(user);
        return AuthResponse.bearer(token.value(), token.expiresInSeconds());
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(String username) {
        // A valid token for a user that no longer exists must not be honoured. The query selects only the four
        // fields of UserResponse, so the password hash is never loaded for this request.
        return userRepository.findProfileByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("User no longer exists"));
    }
}
