package com.springlab.mapper;

import com.springlab.dto.admin.AdminUserResponse;
import com.springlab.dto.user.UserResponse;
import com.springlab.entity.User;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole());
    }

    public static AdminUserResponse toAdminResponse(User user) {
        return new AdminUserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole(),
                user.isEnabled(), user.getCreatedAt());
    }
}
