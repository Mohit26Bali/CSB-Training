package com.csb.banking.dto;

import com.csb.banking.entity.AccountStatus;

import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code PATCH /api/admin/accounts/{accountNumber}/status}: lets an administrator activate, deactivate or
 * block an account. Received by {@code controller/AdminAccountController}, applied by
 * {@code service/AdminAccountService#updateStatus}.
 */
public record UpdateAccountStatusRequest(
        @NotNull(message = "status is required (ACTIVE, INACTIVE or BLOCKED)")
        AccountStatus status) {
}
