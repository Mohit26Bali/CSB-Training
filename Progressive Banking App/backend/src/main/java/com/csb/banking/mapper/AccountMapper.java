package com.csb.banking.mapper;

import org.springframework.stereotype.Component;

import com.csb.banking.dto.AccountResponse;
import com.csb.banking.entity.Account;

/**
 * Builds the {@link AccountResponse} for an {@link Account}.
 *
 * <p>Injected into {@code service/AccountService} and {@code service/AdminAccountService}, which both need the same
 * output shape. Must be called inside a transaction: it reads {@code account.getCustomer().getUser()}, which is
 * loaded lazily (open-in-view is switched off in application.properties).
 */
@Component
public class AccountMapper {

    public AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getAccountNumber(),
                account.getAccountType(),
                account.getStatus(),
                account.getBalance(),
                account.getCustomer().getUser().getFullName(),
                account.getCreatedAt());
    }
}
