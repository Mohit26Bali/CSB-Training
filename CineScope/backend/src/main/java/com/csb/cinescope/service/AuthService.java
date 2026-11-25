package com.csb.cinescope.service;

import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.csb.cinescope.config.CineScopeProperties;
import com.csb.cinescope.dto.AuthResponse;
import com.csb.cinescope.dto.LoginRequest;
import com.csb.cinescope.dto.RegisterRequest;
import com.csb.cinescope.dto.UserResponse;
import com.csb.cinescope.entity.User;
import com.csb.cinescope.exception.BadRequestException;
import com.csb.cinescope.exception.ConflictException;
import com.csb.cinescope.exception.NotFoundException;
import com.csb.cinescope.repository.UserRepository;
import com.csb.cinescope.security.JwtService;
import com.csb.cinescope.security.PasswordPolicy;

/**
 * Registration, login and "who am I".
 *
 * <p>Called by {@code controller/AuthController}. Collaborators: {@link LoginAttemptService} (lockout), the
 * {@code AuthenticationManager} from {@code config/SecurityConfig} (verifies the password hash through
 * {@link CustomUserDetailsService}), {@code security/JwtService} (issues the token) and {@code repository/UserRepository}.
 * New accounts get the default region (India) from {@code config/CineScopeProperties}.
 */
@Service
public class AuthService {

    private static final String INVALID_LOGIN = "Invalid username or password";

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;
    private final CineScopeProperties properties;

    public AuthService(AuthenticationManager authenticationManager, UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, LoginAttemptService loginAttemptService, CineScopeProperties properties) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
        this.properties = properties;
    }

    public AuthResponse login(LoginRequest request) {
        String username = normalise(request.username());
        loginAttemptService.checkNotLocked(username);
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, request.password()));
        } catch (AuthenticationException ex) {
            loginAttemptService.recordFailure(username);
            // The same message for an unknown user, a wrong password and a disabled account: no user enumeration.
            throw new BadCredentialsException(INVALID_LOGIN);
        }
        loginAttemptService.recordSuccess(username);
        User user = userRepository.findByUsername(username).orElseThrow(() -> new BadCredentialsException(INVALID_LOGIN));
        return toAuthResponse(user);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = normalise(request.username());
        String email = normalise(request.email());
        String identityProblem = PasswordPolicy.identityProblem(request.password(), username, email);
        if (identityProblem != null) {
            throw new BadRequestException(identityProblem);
        }
        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("Username '" + username + "' is already taken");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        try {
            User user = userRepository.saveAndFlush(new User(username, email, passwordEncoder.encode(request.password()),
                    request.displayName().trim(), properties.defaultRegion()));
            return toAuthResponse(user);
        } catch (DataIntegrityViolationException ex) {
            // Two simultaneous registrations passed the checks above; the unique constraint caught the loser.
            throw new ConflictException("Username or email is already registered");
        }
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(String username) {
        User user = userRepository.findByUsername(username).orElseThrow(() -> new NotFoundException("User not found"));
        return toUserResponse(user);
    }

    private AuthResponse toAuthResponse(User user) {
        return sessionFor(user);
    }

    /** A fresh login token (carrying the account's current token version) with the profile data the UI shows. */
    public AuthResponse sessionFor(User user) {
        return new AuthResponse(jwtService.generateToken(user.getUsername(), user.getTokenVersion()), jwtService.getExpiresInSeconds(),
                user.getUsername(), user.getDisplayName(), user.getRegion(), user.getRole().name());
    }

    public static UserResponse toUserResponse(User user) {
        return new UserResponse(user.getUsername(), user.getEmail(), user.getDisplayName(), user.getRegion(), user.getRole().name());
    }

    private static String normalise(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
