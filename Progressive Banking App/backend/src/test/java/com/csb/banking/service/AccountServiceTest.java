package com.csb.banking.service;

import static com.csb.banking.service.Fixtures.account;
import static com.csb.banking.service.Fixtures.adminAuth;
import static com.csb.banking.service.Fixtures.customer;
import static com.csb.banking.service.Fixtures.customerAuth;
import static com.csb.banking.service.Fixtures.money;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.csb.banking.dto.AccountResponse;
import com.csb.banking.dto.BalanceResponse;
import com.csb.banking.dto.CreateAccountRequest;
import com.csb.banking.dto.UpdateAccountStatusRequest;
import com.csb.banking.entity.Account;
import com.csb.banking.entity.AccountStatus;
import com.csb.banking.entity.AccountType;
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
 * Unit tests of {@link AccountService} (customer-side account data) and {@link AdminAccountService}: opening accounts,
 * the opening-deposit ledger row, balance visibility rules and who may see which account. Repositories are mocks.
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock AccountRepository accounts;
    @Mock CustomerRepository customers;
    @Mock TransactionRepository transactions;
    @Mock AccountNumberGenerator numbers;

    AccountService service;
    AdminAccountService admin;
    Customer ravi = customer("ravi");

    @BeforeEach
    void setUp() {
        AccountMapper mapper = new AccountMapper();
        service = new AccountService(accounts, customers, transactions, numbers, mapper, new AccountAccessPolicy());
        admin = new AdminAccountService(accounts, mapper);
    }

    // ---- opening an account ----

    @Test
    void openingAnAccountWithADepositSavesAnActiveAccountAndALedgerRow() {
        when(customers.findByUserUsername("ravi")).thenReturn(Optional.of(ravi));
        when(numbers.next()).thenReturn("1000000001");
        when(accounts.saveAndFlush(any(Account.class))).thenAnswer(call -> call.getArgument(0));

        AccountResponse response = service.createAccount("ravi", new CreateAccountRequest(AccountType.CURRENT, money("2500.5")));

        assertEquals("1000000001", response.accountNumber());
        assertEquals(AccountStatus.ACTIVE, response.status());
        assertEquals(AccountType.CURRENT, response.accountType());
        assertEquals(0, money("2500.50").compareTo(response.balance()));
        assertEquals("ravi full name", response.customerName());

        ArgumentCaptor<Transaction> row = ArgumentCaptor.forClass(Transaction.class);
        verify(transactions).save(row.capture());
        assertEquals(TransactionType.DEPOSIT, row.getValue().getType());
        assertEquals(TransactionDirection.CREDIT, row.getValue().getDirection());
        assertEquals(TransactionStatus.SUCCESS, row.getValue().getStatus());
        assertEquals(0, money("2500.50").compareTo(row.getValue().getBalanceAfter()));
    }

    @Test
    void openingAnAccountWithoutADepositWritesNoLedgerRow() {
        when(customers.findByUserUsername("ravi")).thenReturn(Optional.of(ravi));
        when(numbers.next()).thenReturn("1000000001");
        when(accounts.saveAndFlush(any(Account.class))).thenAnswer(call -> call.getArgument(0));

        AccountResponse zero = service.createAccount("ravi", new CreateAccountRequest(AccountType.SAVINGS, null));
        AccountResponse explicitZero = service.createAccount("ravi", new CreateAccountRequest(AccountType.SAVINGS, BigDecimal.ZERO));

        assertEquals(0, BigDecimal.ZERO.compareTo(zero.balance()));
        assertEquals(0, BigDecimal.ZERO.compareTo(explicitZero.balance()));
        verify(transactions, never()).save(any());
    }

    @Test
    void aUserWithoutACustomerProfileCannotOpenAnAccount() {
        when(customers.findByUserUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.createAccount("ghost", new CreateAccountRequest(AccountType.SAVINGS, null)));
    }

    @Test
    void aNumberCollisionInTheDatabaseBecomesADuplicateAccountError() {
        when(customers.findByUserUsername("ravi")).thenReturn(Optional.of(ravi));
        when(numbers.next()).thenReturn("1000000001");
        when(accounts.saveAndFlush(any(Account.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThrows(DuplicateAccountException.class,
                () -> service.createAccount("ravi", new CreateAccountRequest(AccountType.SAVINGS, null)));
        verify(transactions, never()).save(any());
    }

    // ---- reading ----

    @Test
    void myAccountsListsTheCallersAccounts() {
        when(customers.findByUserUsername("ravi")).thenReturn(Optional.of(ravi));
        when(accounts.findByCustomerOrderByCreatedAtDesc(ravi))
                .thenReturn(List.of(account("1000000002", ravi, "5.00"), account("1000000001", ravi, "10.00")));

        List<AccountResponse> mine = service.getMyAccounts("ravi");

        assertEquals(List.of("1000000002", "1000000001"), mine.stream().map(AccountResponse::accountNumber).toList());
    }

    @Test
    void ownerAndAdminCanReadAnAccountButAnotherCustomerGetsNotFound() {
        when(accounts.findByAccountNumber("1000000001")).thenReturn(Optional.of(account("1000000001", ravi, "10.00")));

        assertEquals("1000000001", service.getAccount("1000000001", customerAuth("ravi")).accountNumber());
        assertEquals("1000000001", service.getAccount("1000000001", adminAuth()).accountNumber());
        assertThrows(AccountNotFoundException.class, () -> service.getAccount("1000000001", customerAuth("priya")));
    }

    @Test
    void anUnknownAccountIsNotFound() {
        when(accounts.findByAccountNumber("9999999999")).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class, () -> service.getAccount("9999999999", customerAuth("ravi")));
    }

    @Test
    void balanceIsOnlyShownForAnActiveAccount() {
        when(accounts.findByAccountNumber("1000000001")).thenReturn(Optional.of(account("1000000001", ravi, "50000.00")));
        when(accounts.findByAccountNumber("1000000002")).thenReturn(Optional.of(account("1000000002", ravi, "1.00", AccountStatus.BLOCKED)));

        BalanceResponse balance = service.getBalance("1000000001", customerAuth("ravi"));
        assertEquals(0, money("50000.00").compareTo(balance.balance()));

        assertThrows(AccountInactiveException.class, () -> service.getBalance("1000000002", customerAuth("ravi")));
    }

    // ---- administrator ----

    @Test
    void anAdminCanChangeTheStatusOfAnAccount() {
        Account a = account("1000000001", ravi, "10.00");
        when(accounts.findByAccountNumber("1000000001")).thenReturn(Optional.of(a));
        when(accounts.save(a)).thenReturn(a);

        AccountResponse response = admin.updateStatus("1000000001", new UpdateAccountStatusRequest(AccountStatus.BLOCKED));

        assertEquals(AccountStatus.BLOCKED, a.getStatus());
        assertEquals(AccountStatus.BLOCKED, response.status());
    }

    @Test
    void changingTheStatusOfAnUnknownAccountIsNotFound() {
        when(accounts.findByAccountNumber("9999999999")).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class,
                () -> admin.updateStatus("9999999999", new UpdateAccountStatusRequest(AccountStatus.ACTIVE)));
    }

    @Test
    void anAdminSeesEveryAccount() {
        when(accounts.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(
                account("1000000001", ravi, "10.00"), account("1000000002", customer("priya"), "20.00")));

        assertEquals(2, admin.getAllAccounts().size());
    }
}
