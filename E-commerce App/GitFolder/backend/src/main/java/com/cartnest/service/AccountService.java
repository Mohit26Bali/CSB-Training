package com.cartnest.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cartnest.dto.PasswordChangeRequest;
import com.cartnest.dto.ProfileUpdateRequest;
import com.cartnest.dto.UserResponse;
import com.cartnest.entity.User;
import com.cartnest.exception.BadRequestException;
import com.cartnest.exception.ResourceNotFoundException;
import com.cartnest.repository.UserRepository;

@Service
@Transactional
public class AccountService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponse updateProfile(Long userId, ProfileUpdateRequest request) {
        User user = find(userId);
        user.setFullName(request.fullName().trim());
        user.setPhone(request.phone());
        return AuthService.toResponse(user);
    }

    public void changePassword(Long userId, PasswordChangeRequest request) {
        User user = find(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BadRequestException("New password must be different from the current one");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    private User find(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
