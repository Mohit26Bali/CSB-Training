package com.icc.assignment.service;

import com.icc.assignment.dto.LoginRequest;
import com.icc.assignment.dto.LoginResponse;
import com.icc.assignment.entity.AppUser;
import com.icc.assignment.exception.InvalidCredentialsException;
import com.icc.assignment.mapper.UserMapper;
import com.icc.assignment.repository.AppUserRepository;
import com.icc.common.audit.AuditService;
import com.icc.common.jwt.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AppUserRepository userRepository;
    @Mock
    private TokenService tokenService;
    @Mock
    private AuditService auditService;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(userRepository, encoder, tokenService, new UserMapper(), auditService);
    }

    private AppUser user(boolean active) {
        return new AppUser(2L, "sd.engineer", encoder.encode("secret-pw"), "Sam Service",
                "sam@icc.local", Role.SUPPORT_ENGINEER, 1L, active);
    }

    @Test
    void correctPassword_returnsTokenAndUser_andAuditsTheLogin() {
        AppUser engineer = user(true);
        when(userRepository.findByUsername("sd.engineer")).thenReturn(Optional.of(engineer));
        when(tokenService.createToken(engineer)).thenReturn("the-token");
        when(tokenService.expirySeconds()).thenReturn(3600L);

        LoginResponse response = service.login(new LoginRequest("sd.engineer", "secret-pw"));

        assertEquals("the-token", response.token());
        assertEquals("Bearer", response.tokenType());
        assertEquals(3600, response.expiresInSeconds());
        assertEquals("sd.engineer", response.user().username());
        verify(auditService).record(eq(2L), eq("LOGIN_SUCCESS"), eq("USER"), eq(2L), isNull());
    }

    @Test
    void wrongPassword_isRejected_andAuditedAsFailed() {
        when(userRepository.findByUsername("sd.engineer")).thenReturn(Optional.of(user(true)));

        assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequest("sd.engineer", "wrong")));

        verify(auditService).record(isNull(), eq("LOGIN_FAILED"), eq("USER"), eq(2L), anyString());
    }

    @Test
    void unknownUser_getsTheSameError() {
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        InvalidCredentialsException error = assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequest("nobody", "x")));

        assertEquals("Invalid username or password", error.getMessage());
        verify(auditService).record(isNull(), eq("LOGIN_FAILED"), eq("USER"), isNull(), anyString());
    }

    @Test
    void inactiveUser_isRejectedEvenWithTheRightPassword() {
        when(userRepository.findByUsername("sd.engineer")).thenReturn(Optional.of(user(false)));

        assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequest("sd.engineer", "secret-pw")));
    }
}
