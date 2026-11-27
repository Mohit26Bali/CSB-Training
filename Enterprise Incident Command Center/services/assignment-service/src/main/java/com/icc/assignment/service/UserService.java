package com.icc.assignment.service;

import com.icc.assignment.dto.UserResponse;
import com.icc.assignment.dto.UserSummaryResponse;
import com.icc.assignment.mapper.UserMapper;
import com.icc.assignment.repository.AppUserRepository;
import com.icc.common.error.NotFoundException;
import com.icc.common.jwt.JwtClaims;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final AppUserRepository userRepository;
    private final UserMapper userMapper;

    public UserService(AppUserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    public UserResponse getMe(JwtClaims claims) {
        return userRepository.findById(claims.userId())
                .map(userMapper::toResponse)
                .orElseThrow(() -> new NotFoundException("User", claims.userId()));
    }

    public List<UserSummaryResponse> listUsers() {
        return userRepository.findAll(Sort.by("id")).stream()
                .map(userMapper::toSummary)
                .toList();
    }
}
