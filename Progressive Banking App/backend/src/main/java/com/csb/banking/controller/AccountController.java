package com.csb.banking.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.csb.banking.dto.AccountResponse;
import com.csb.banking.dto.BalanceResponse;
import com.csb.banking.dto.CreateAccountRequest;
import com.csb.banking.service.AccountService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * {@code /api/accounts}: the account endpoints from the project description (section 17).
 *
 * <pre>
 * POST /api/accounts                          open an account (CUSTOMER)
 * GET  /api/accounts/me                       my accounts (CUSTOMER)
 * GET  /api/accounts/{accountNumber}          account details (owner or ADMIN)
 * GET  /api/accounts/{accountNumber}/balance  balance (owner or ADMIN)
 * </pre>
 * Requires a login (anyRequest().authenticated() in {@code config/SecurityConfig}). The {@code Authentication}
 * argument is the user put into the security context by {@code security/JwtAuthenticationFilter}; it is passed to
 * {@code service/AccountService}, which decides what that user may see. Money movement and history are in
 * {@code TransactionController}.
 */
@Tag(name = "Accounts", description = "Open accounts and read account data (customers see only their own)")
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Operation(summary = "Open an account", description = "CUSTOMER only. An optional initial deposit is recorded as the first ledger entry.")
    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request,
                                                  Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.createAccount(authentication.getName(), request));
    }

    @Operation(summary = "My accounts", description = "CUSTOMER only.")
    @GetMapping("/me")
    public List<AccountResponse> myAccounts(Authentication authentication) {
        return accountService.getMyAccounts(authentication.getName());
    }

    @Operation(summary = "Account details", description = "The owner or an ADMIN. Someone else's account answers 404.")
    @GetMapping("/{accountNumber}")
    public AccountResponse get(@PathVariable String accountNumber, Authentication authentication) {
        return accountService.getAccount(accountNumber, authentication);
    }

    @Operation(summary = "Account balance", description = "Only ACTIVE accounts report a balance (422 otherwise).")
    @GetMapping("/{accountNumber}/balance")
    public BalanceResponse balance(@PathVariable String accountNumber, Authentication authentication) {
        return accountService.getBalance(accountNumber, authentication);
    }
}
