package com.csb.cinescope.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.csb.cinescope.config.CineScopeProperties;
import com.csb.cinescope.dto.AuthResponse;
import com.csb.cinescope.dto.LoginRequest;
import com.csb.cinescope.dto.RegisterRequest;
import com.csb.cinescope.dto.UserResponse;
import com.csb.cinescope.entity.User;
import com.csb.cinescope.exception.ConflictException;
import com.csb.cinescope.exception.NotFoundException;
import com.csb.cinescope.exception.TooManyAttemptsException;
import com.csb.cinescope.repository.UserRepository;
import com.csb.cinescope.security.JwtService;

/**
 * Unit tests of {@link AuthService}: registration (what is saved, how input is normalised, duplicates), login (the same
 * message for every failure, the lockout) and "who am I". The password check itself belongs to Spring Security's
 * AuthenticationManager and is mocked; the real JwtService and LoginAttemptService are used. No Spring, no database.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock AuthenticationManager authenticationManager;
    @Mock UserRepository users;
    @Mock PasswordEncoder passwordEncoder;

    JwtService jwt = new JwtService("unit-test-secret-unit-test-secret-1234", 720);
    LoginAttemptService attempts = new LoginAttemptService(3, 15);
    AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(authenticationManager, users, passwordEncoder, jwt, attempts, new CineScopeProperties("IN", "en-US"));
    }

    private RegisterRequest registration() {
        return new RegisterRequest("  Asha ", " Asha@Example.com ", "Secret-Pass-1!", " Asha Rao ");
    }

    // ---- register ----

    @Test
    void registrationSavesAHashedUserWithNormalisedValuesAndTheDefaultRegion() {
        when(passwordEncoder.encode("Secret-Pass-1!")).thenReturn("ENCODED");
        when(users.saveAndFlush(any(User.class))).thenAnswer(call -> call.getArgument(0));

        AuthResponse response = service.register(registration());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(saved.capture());
        assertEquals("asha", saved.getValue().getUsername());
        assertEquals("asha@example.com", saved.getValue().getEmail());
        assertEquals("ENCODED", saved.getValue().getPassword());
        assertEquals("Asha Rao", saved.getValue().getDisplayName());
        assertEquals("IN", saved.getValue().getRegion());
        assertEquals("asha", jwt.extractUsername(response.token()));
        assertEquals(720 * 60, response.expiresInSeconds());
    }

    @Test
    void aTakenUsernameOrEmailIsRefusedAndNothingIsSaved() {
        when(users.existsByUsername("asha")).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.register(registration()));

        when(users.existsByUsername("asha")).thenReturn(false);
        when(users.existsByEmailIgnoreCase("asha@example.com")).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.register(registration()));

        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void aRaceAgainstTheUniqueConstraintBecomesAConflict() {
        when(passwordEncoder.encode(any())).thenReturn("ENCODED");
        when(users.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("unique"));

        assertThrows(ConflictException.class, () -> service.register(registration()));
    }

    // ---- login ----

    @Test
    void loginWithValidCredentialsReturnsAUsableToken() {
        when(users.findByUsername("ravi")).thenReturn(Optional.of(new User("ravi", "ravi@example.com", "hash", "Ravi", "IN")));

        AuthResponse response = service.login(new LoginRequest("  Ravi ", "Secret-Pass-1!"));

        assertEquals("ravi", response.username());
        assertEquals("Ravi", response.displayName());
        assertEquals("ravi", jwt.extractUsername(response.token()));
    }

    @Test
    void anyLoginFailureGivesTheSameGenericMessage() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("internal detail"));

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () -> service.login(new LoginRequest("nobody", "x")));

        assertEquals("Invalid username or password", ex.getMessage());
    }

    @Test
    void afterTooManyFailuresTheUsernameIsLockedEvenForTheCorrectPassword() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));
        for (int i = 0; i < 3; i++) {
            assertThrows(BadCredentialsException.class, () -> service.login(new LoginRequest("ravi", "wrong")));
        }

        assertThrows(TooManyAttemptsException.class, () -> service.login(new LoginRequest("ravi", "Secret-Pass-1!")));
        verify(authenticationManager, times(3)).authenticate(any()); // the locked attempt never reached the password check
    }

    // ---- current user ----

    @Test
    void currentUserReturnsTheProfileOrNotFound() {
        when(users.findByUsername("ravi")).thenReturn(Optional.of(new User("ravi", "ravi@example.com", "hash", "Ravi", "IN")));
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());

        UserResponse me = service.currentUser("ravi");

        assertEquals(new UserResponse("ravi", "ravi@example.com", "Ravi", "IN", "USER"), me);
        assertNotNull(me.email());
        assertThrows(NotFoundException.class, () -> service.currentUser("ghost"));
    }
}
