package com.csb.banking.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.csb.banking.dto.AccountResponse;
import com.csb.banking.dto.BalanceResponse;
import com.csb.banking.dto.CreateAccountRequest;
import com.csb.banking.entity.Account;
import com.csb.banking.entity.Customer;
import com.csb.banking.entity.Transaction;
import com.csb.banking.entity.TransactionDirection;
import com.csb.banking.entity.TransactionStatus;
import com.csb.banking.entity.TransactionType;
import com.csb.banking.exception.AccountInactiveException;
import com.csb.banking.exception.AccountNotFoundException;
import com.csb.banking.exception.DuplicateAccountException;
import com.csb.banking.exception.ResourceNotFoundException;
import com.csb.banking.mapper.AccountMapper;
import com.csb.banking.repository.AccountRepository;
import com.csb.banking.repository.CustomerRepository;
import com.csb.banking.repository.TransactionRepository;

/**
 * Customer-facing account data: open an account, list own accounts, view an account, read its balance.
 * Moving money (deposit, withdraw, transfer, history) lives in {@link TransactionService}.
 *
 * <p>Called by {@code controller/AccountController}. Uses the account / customer / transaction repositories,
 * {@link AccountNumberGenerator} and {@code mapper/AccountMapper}; throws {@code exception/*} on rule violations.
 *
 * <p>Access rule (project description, section 19): a CUSTOMER sees only their own accounts, an ADMIN sees all.
 * {@link AccountAccessPolicy} enforces it and answers "not found" for someone else's account so existence is not
 * leaked.
 */
@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;
    private final AccountNumberGenerator accountNumberGenerator;
    private final AccountMapper accountMapper;
    private final AccountAccessPolicy accessPolicy;

    public AccountService(AccountRepository accountRepository, CustomerRepository customerRepository,
                          TransactionRepository transactionRepository,
                          AccountNumberGenerator accountNumberGenerator, AccountMapper accountMapper,
                          AccountAccessPolicy accessPolicy) {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.transactionRepository = transactionRepository;
        this.accountNumberGenerator = accountNumberGenerator;
        this.accountMapper = accountMapper;
        this.accessPolicy = accessPolicy;
    }

    /** Opens an ACTIVE account for the logged-in customer; a positive initial deposit is recorded in the ledger. */
    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional
    public AccountResponse createAccount(String username, CreateAccountRequest request) {
        Customer customer = customerRepository.findByUserUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found for " + username));

        BigDecimal opening = request.initialDeposit() == null
                ? BigDecimal.ZERO.setScale(2)
                : request.initialDeposit().setScale(2, RoundingMode.HALF_UP);

        Account account = new Account(accountNumberGenerator.next(), request.accountType(), customer, opening);
        try {
            accountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateAccountException("Could not allocate a unique account number, please retry");
        }

        if (opening.signum() > 0) {
            transactionRepository.save(new Transaction(account, UUID.randomUUID().toString(),
                    TransactionType.DEPOSIT, TransactionDirection.CREDIT, opening, TransactionStatus.SUCCESS,
                    "Opening deposit", null, opening));
        }
        return accountMapper.toResponse(account);
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional(readOnly = true)
    public List<AccountResponse> getMyAccounts(String username) {
        Customer customer = customerRepository.findByUserUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found for " + username));
        return accountRepository.findByCustomerOrderByCreatedAtDesc(customer).stream()
                .map(accountMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(String accountNumber, Authentication authentication) {
        return accountMapper.toResponse(findAccessible(accountNumber, authentication));
    }

    /** Balance inquiry: only an ACTIVE account reports its balance (project description, section 7). */
    @Transactional(readOnly = true)
    public BalanceResponse getBalance(String accountNumber, Authentication authentication) {
        Account account = findAccessible(accountNumber, authentication);
        if (!account.isActive()) {
            throw new AccountInactiveException(accountNumber);
        }
        return new BalanceResponse(account.getAccountNumber(), account.getBalance(), LocalDateTime.now());
    }

    private Account findAccessible(String accountNumber, Authentication authentication) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
        accessPolicy.requireReadAccess(account, authentication);
        return account;
    }
}
