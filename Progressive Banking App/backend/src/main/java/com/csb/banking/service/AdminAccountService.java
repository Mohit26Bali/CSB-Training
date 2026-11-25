package com.csb.banking.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.csb.banking.dto.AccountResponse;
import com.csb.banking.dto.UpdateAccountStatusRequest;
import com.csb.banking.entity.Account;
import com.csb.banking.exception.AccountNotFoundException;
import com.csb.banking.mapper.AccountMapper;
import com.csb.banking.repository.AccountRepository;

/**
 * Administrator operations on accounts: list every account, activate / deactivate / block one.
 *
 * <p>Kept apart from {@link AccountService} so customer rules and administrator rules never mix. Called by
 * {@code controller/AdminAccountController}; the URL prefix {@code /api/admin/**} is already restricted to ADMIN in
 * {@code config/SecurityConfig}, and the {@code @PreAuthorize} here repeats the check at method level as a second
 * line of defence (for example if the service is ever called from somewhere else).
 */
@Service
public class AdminAccountService {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;

    public AdminAccountService(AccountRepository accountRepository, AccountMapper accountMapper) {
        this.accountRepository = accountRepository;
        this.accountMapper = accountMapper;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<AccountResponse> getAllAccounts() {
        return accountRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(accountMapper::toResponse)
                .collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public AccountResponse updateStatus(String accountNumber, UpdateAccountStatusRequest request) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
        account.setStatus(request.status());
        return accountMapper.toResponse(accountRepository.save(account));
    }
}
