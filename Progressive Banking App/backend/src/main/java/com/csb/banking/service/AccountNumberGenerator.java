package com.csb.banking.service;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

import com.csb.banking.repository.AccountRepository;

/**
 * Produces unique 10-digit account numbers starting with "10" (business rule: every account number is unique).
 *
 * <p>Used by {@link AccountService#createAccount} and {@code config/DataInitializer}. Numbers are random rather
 * than sequential so one cannot guess a neighbour's account. Uniqueness is checked against
 * {@code repository/AccountRepository}; the unique database constraint on {@code accounts.account_number} remains
 * the final guard against two requests picking the same number at the same moment.
 */
@Component
public class AccountNumberGenerator {

    private static final int MAX_ATTEMPTS = 10;

    private final AccountRepository accountRepository;
    private final SecureRandom random = new SecureRandom();

    public AccountNumberGenerator(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public String next() {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            String candidate = "10" + String.format("%08d", random.nextInt(100_000_000));
            if (!accountRepository.existsByAccountNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique account number");
    }
}
