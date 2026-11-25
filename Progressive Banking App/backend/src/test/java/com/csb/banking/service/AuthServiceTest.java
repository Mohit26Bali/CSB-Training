package com.csb.banking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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

import com.csb.banking.dto.AuthResponse;
import com.csb.banking.dto.LoginRequest;
import com.csb.banking.dto.RegisterRequest;
import com.csb.banking.dto.UserResponse;
import com.csb.banking.entity.Customer;
import com.csb.banking.entity.Role;
import com.csb.banking.entity.User;
import com.csb.banking.exception.DuplicateUserException;
import com.csb.banking.exception.ResourceNotFoundException;
import com.csb.banking.exception.TooManyAttemptsException;
import com.csb.banking.repository.CustomerRepository;
import com.csb.banking.repository.UserRepository;
import com.csb.banking.security.JwtService;

/**
 * Unit tests of {@link AuthService}: login (including the "same message for every failure" rule and the lockout),
 * registration (what gets saved, duplicates) and "who am I". The password check itself belongs to Spring Security's
 * AuthenticationManager, which is mocked; the real JwtService and LoginAttemptService are used.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock AuthenticationManager authenticationManager;
    @Mock UserRepository users;
    @Mock CustomerRepository customers;
    @Mock PasswordEncoder passwordEncoder;

    JwtService jwt = new JwtService("unit-test-secret-unit-test-secret-1234", 30);
    LoginAttemptService attempts = new LoginAttemptService(3, 15);
    AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(authenticationManager, users, customers, passwordEncoder, jwt, attempts);
    }

    private RegisterRequest registration() {
        return new RegisterRequest("  Asha ", "Violet-Rain-123!", " Asha Rao ", " Asha@Example.com ", "9000000001", " 1 Lake Road ");
    }

    // ---- login ----

    @Test
    void loginWithValidCredentialsReturnsAUsableToken() {
        when(users.findByUsername("ravi")).thenReturn(Optional.of(new User("ravi", "hash", "Ravi Kumar", Role.CUSTOMER)));

        AuthResponse response = service.login(new LoginRequest("  Ravi ", "customer123"));

        assertEquals("ravi", response.username());
        assertEquals("Ravi Kumar", response.fullName());
        assertEquals("CUSTOMER", response.role());
        assertEquals(30 * 60, response.expiresInSeconds());
        assertEquals("ravi", jwt.extractUsername(response.token()));
    }

    @Test
    void anyLoginFailureGivesTheSameGenericMessage() {
        when(authenticationManager.authenticate(any())).thenThrow(new org.springframework.security.authentication.BadCredentialsException("whatever internal"));

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () -> service.login(new LoginRequest("nobody", "x")));

        assertEquals("Invalid username or password", ex.getMessage());
    }

    @Test
    void afterTooManyFailuresTheUsernameIsLockedEvenForTheCorrectPassword() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad"))
                .thenThrow(new BadCredentialsException("bad"))
                .thenThrow(new BadCredentialsException("bad"));
        for (int i = 0; i < 3; i++) {
            assertThrows(BadCredentialsException.class, () -> service.login(new LoginRequest("ravi", "wrong")));
        }

        assertThrows(TooManyAttemptsException.class, () -> service.login(new LoginRequest("ravi", "customer123")));
        // the locked attempt never reached the password check (3 failed checks only)
        verify(authenticationManager, org.mockito.Mockito.times(3)).authenticate(any());
    }

    // ---- register ----

    @Test
    void registrationCreatesAHashedCustomerLoginAndAProfileWithNormalisedValues() {
        when(users.existsByUsername("asha")).thenReturn(false);
        when(customers.existsByEmailIgnoreCase("asha@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Violet-Rain-123!")).thenReturn("ENCODED");
        when(users.saveAndFlush(any(User.class))).thenAnswer(call -> call.getArgument(0));

        AuthResponse response = service.register(registration());

        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(user.capture());
        assertEquals("asha", user.getValue().getUsername());
        assertEquals("ENCODED", user.getValue().getPassword());
        assertEquals("Asha Rao", user.getValue().getFullName());
        assertEquals(Role.CUSTOMER, user.getValue().getRole());

        ArgumentCaptor<Customer> customer = ArgumentCaptor.forClass(Customer.class);
        verify(customers).saveAndFlush(customer.capture());
        assertEquals("asha@example.com", customer.getValue().getEmail());
        assertEquals("1 Lake Road", customer.getValue().getAddress());
        assertNotNull(response.token());
        assertEquals("CUSTOMER", response.role());
    }

    @Test
    void registeringATakenUsernameOrEmailIsRefusedAndSavesNothing() {
        when(users.existsByUsername("asha")).thenReturn(true);
        assertThrows(DuplicateUserException.class, () -> service.register(registration()));

        when(users.existsByUsername("asha")).thenReturn(false);
        when(customers.existsByEmailIgnoreCase("asha@example.com")).thenReturn(true);
        assertThrows(DuplicateUserException.class, () -> service.register(registration()));

        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void aRaceAgainstTheUniqueConstraintBecomesADuplicateError() {
        when(passwordEncoder.encode(any())).thenReturn("ENCODED");
        when(users.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("unique"));

        assertThrows(DuplicateUserException.class, () -> service.register(registration()));
    }

    // ---- current user ----

    @Test
    void currentUserReturnsTheProfileOrNotFound() {
        when(users.findByUsername("ravi")).thenReturn(Optional.of(new User("ravi", "hash", "Ravi Kumar", Role.CUSTOMER)));
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());

        UserResponse me = service.currentUser("ravi");
        assertEquals(new UserResponse("ravi", "Ravi Kumar", "CUSTOMER"), me);
        assertThrows(ResourceNotFoundException.class, () -> service.currentUser("ghost"));
    }
}
