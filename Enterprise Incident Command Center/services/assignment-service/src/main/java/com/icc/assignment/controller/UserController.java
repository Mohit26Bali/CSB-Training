package com.icc.assignment.controller;

import com.icc.assignment.dto.UserResponse;
import com.icc.assignment.dto.UserSummaryResponse;
import com.icc.assignment.service.UserService;
import com.icc.common.jwt.JwtClaims;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal JwtClaims claims) {
        return userService.getMe(claims);
    }

    @GetMapping
    public List<UserSummaryResponse> list() {
        return userService.listUsers();
    }
}
