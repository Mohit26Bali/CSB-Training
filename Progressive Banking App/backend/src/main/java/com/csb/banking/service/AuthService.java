package com.csb.banking.service;

import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.csb.banking.dto.AuthResponse;
import com.csb.banking.dto.LoginRequest;
import com.csb.banking.dto.RegisterRequest;
import com.csb.banking.dto.UserResponse;
import com.csb.banking.entity.Customer;
import com.csb.banking.entity.Role;
import com.csb.banking.entity.User;
import com.csb.banking.exception.DuplicateUserException;
import com.csb.banking.exception.ResourceNotFoundException;
import com.csb.banking.exception.WeakPasswordException;
import com.csb.banking.repository.CustomerRepository;
import com.csb.banking.repository.UserRepository;
import com.csb.banking.security.JwtService;
import com.csb.banking.security.PasswordPolicy;

/**
 * Login, customer registration and "who am I".
 *
 * <p>Called by {@code controller/AuthController}. Collaborators: {@link LoginAttemptService} (lockout),
 * the {@code AuthenticationManager} from {@code config/SecurityConfig} (verifies the password against the BCrypt
 * hash via {@link CustomUserDetailsService}), {@code security/JwtService} (issues the token), and the user /
 * customer repositories. Registration always creates a CUSTOMER; administrators exist only through seeding.
 */
@Service
public class AuthService {

    private static final String INVALID_LOGIN = "Invalid username or password";

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;

    public AuthService(AuthenticationManager authenticationManager, UserRepository userRepository,
                       CustomerRepository customerRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, LoginAttemptService loginAttemptService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
    }

    public AuthResponse login(LoginRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        loginAttemptService.checkNotLocked(username);
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, request.password()));
        } catch (AuthenticationException ex) {
            loginAttemptService.recordFailure(username);
            // Same message for unknown user, wrong password and disabled account: no user enumeration.
            throw new BadCredentialsException(INVALID_LOGIN);
        }
        loginAttemptService.recordSuccess(username);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException(INVALID_LOGIN));
        return toAuthResponse(user);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String identityProblem = PasswordPolicy.identityProblem(request.password(), username, email);
        if (identityProblem != null) {
            throw new WeakPasswordException(identityProblem);
        }
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateUserException("Username '" + username + "' is already taken");
        }
        if (customerRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateUserException("Email '" + email + "' is already registered");
        }
        try {
            User user = userRepository.saveAndFlush(new User(username,
                    passwordEncoder.encode(request.password()), request.fullName().trim(), Role.CUSTOMER));
            customerRepository.saveAndFlush(
                    new Customer(user, email, request.mobile(), request.address().trim()));
            return toAuthResponse(user);
        } catch (DataIntegrityViolationException ex) {
            // Two simultaneous registrations passed the checks above; the unique constraints caught the loser.
            throw new DuplicateUserException("Username or email is already registered");
        }
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        return new UserResponse(user.getUsername(), user.getFullName(), user.getRole().name());
    }

    private AuthResponse toAuthResponse(User user) {
        String role = user.getRole().name();
        return new AuthResponse(jwtService.generateToken(user.getUsername(), role),
                jwtService.getExpiresInSeconds(), user.getUsername(), user.getFullName(), role);
    }
}
