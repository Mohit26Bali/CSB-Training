package com.icc.assignment.service;

import com.icc.assignment.dto.LoginRequest;
import com.icc.assignment.dto.LoginResponse;
import com.icc.assignment.entity.AppUser;
import com.icc.assignment.exception.InvalidCredentialsException;
import com.icc.assignment.mapper.UserMapper;
import com.icc.assignment.repository.AppUserRepository;
import com.icc.common.audit.AuditService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final UserMapper userMapper;
    private final AuditService auditService;

    public AuthService(AppUserRepository userRepository, PasswordEncoder passwordEncoder,
                       TokenService tokenService, UserMapper userMapper, AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.userMapper = userMapper;
        this.auditService = auditService;
    }

    // Not @Transactional on purpose: the LOGIN_FAILED audit row must survive the exception below.
    public LoginResponse login(LoginRequest request) {
        AppUser user = userRepository.findByUsername(request.username()).orElse(null);

        boolean valid = user != null
                && user.isActive()
                && passwordEncoder.matches(request.password(), user.getPasswordHash());

        if (!valid) {
            Long userId = user != null ? user.getId() : null;
            auditService.record(null, "LOGIN_FAILED", "USER", userId, "Failed login attempt");
            throw new InvalidCredentialsException();
        }

        auditService.record(user.getId(), "LOGIN_SUCCESS", "USER", user.getId(), null);
        return new LoginResponse(tokenService.createToken(user), "Bearer",
                tokenService.expirySeconds(), userMapper.toResponse(user));
    }
}
