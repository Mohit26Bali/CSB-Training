package com.csb.banking.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.csb.banking.dto.AccountResponse;
import com.csb.banking.dto.UpdateAccountStatusRequest;
import com.csb.banking.service.AdminAccountService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * {@code /api/admin/accounts}: administrator-only endpoints.
 *
 * <pre>
 * GET   /api/admin/accounts                         list all accounts
 * PATCH /api/admin/accounts/{accountNumber}/status  activate / deactivate / block
 * </pre>
 * The {@code /api/admin/**} prefix is restricted to ROLE_ADMIN in {@code config/SecurityConfig}, so a customer's
 * token gets 403 before this class is reached. Logic lives in {@code service/AdminAccountService}.
 */
@Tag(name = "Admin", description = "Administrator-only account management")
@RestController
@RequestMapping("/api/admin/accounts")
public class AdminAccountController {

    private final AdminAccountService adminAccountService;

    public AdminAccountController(AdminAccountService adminAccountService) {
        this.adminAccountService = adminAccountService;
    }

    @Operation(summary = "List all accounts", description = "ADMIN only.")
    @GetMapping
    public List<AccountResponse> list() {
        return adminAccountService.getAllAccounts();
    }

    @Operation(summary = "Activate, deactivate or block an account", description = "ADMIN only. Only ACTIVE accounts can take part in transactions.")
    @PatchMapping("/{accountNumber}/status")
    public AccountResponse updateStatus(@PathVariable String accountNumber,
                                        @Valid @RequestBody UpdateAccountStatusRequest request) {
        return adminAccountService.updateStatus(accountNumber, request);
    }
}
