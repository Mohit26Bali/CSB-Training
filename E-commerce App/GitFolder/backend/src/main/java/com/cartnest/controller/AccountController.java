package com.cartnest.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cartnest.dto.PasswordChangeRequest;
import com.cartnest.dto.ProfileUpdateRequest;
import com.cartnest.dto.UserResponse;
import com.cartnest.security.SecurityUser;
import com.cartnest.service.AccountService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/account")
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PutMapping("/profile")
    public UserResponse updateProfile(@AuthenticationPrincipal SecurityUser user,
                                      @Valid @RequestBody ProfileUpdateRequest request) {
        return accountService.updateProfile(user.getId(), request);
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal SecurityUser user,
                               @Valid @RequestBody PasswordChangeRequest request) {
        accountService.changePassword(user.getId(), request);
    }
}
