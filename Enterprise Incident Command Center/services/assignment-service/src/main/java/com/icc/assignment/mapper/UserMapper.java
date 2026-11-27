package com.icc.assignment.mapper;

import com.icc.assignment.dto.UserResponse;
import com.icc.assignment.dto.UserSummaryResponse;
import com.icc.assignment.entity.AppUser;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toResponse(AppUser user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getFullName(),
                user.getEmail(), user.getRole(), user.getTeamId());
    }

    public UserSummaryResponse toSummary(AppUser user) {
        return new UserSummaryResponse(user.getId(), user.getFullName(), user.getRole(), user.getTeamId());
    }
}
