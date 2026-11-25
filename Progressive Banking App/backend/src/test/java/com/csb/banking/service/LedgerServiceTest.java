package com.csb.banking.service;

import static com.csb.banking.service.Fixtures.account;
import static com.csb.banking.service.Fixtures.customer;
import static com.csb.banking.service.Fixtures.customerAuth;
import static com.csb.banking.service.Fixtures.money;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.csb.banking.dto.TransactionResponse;
import com.csb.banking.dto.TransferResponse;
import com.csb.banking.entity.Account;
import com.csb.banking.entity.AccountStatus;
import com.csb.banking.entity.Customer;
import com.csb.banking.entity.Transaction;
import com.csb.banking.entity.TransactionDirection;
import com.csb.banking.entity.TransactionStatus;
import com.csb.banking.entity.TransactionType;
import com.csb.banking.exception.AccountInactiveException;
import com.csb.banking.exception.AccountNotFoundException;
import com.csb.banking.exception.InsufficientBalanceException;
import com.csb.banking.exception.InvalidAmountException;
import com.csb.banking.exception.InvalidTransactionException;
import com.csb.banking.mapper.TransactionMapper;
import com.csb.banking.repository.AccountRepository;
import com.csb.banking.repository.TransactionRepository;

/**
 * Unit tests of the money rules in {@link LedgerService}, with the repositories mocked: no Spring, no database.
 * They pin down every business rule of the project description (sections 8-10, 13, 15) one at a time, including the
 * two properties that matter most for correctness: a rejected operation leaves the balance unchanged, and a
 * transfer locks its accounts in a fixed order. (The same service is also exercised end to end, against a real
 * database, by controller/TransactionApiTest.)
 */
@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock AccountRepository accounts;
    @Mock TransactionRepository transactions;

    LedgerService ledger;
    Customer ravi = customer("ravi");
    Customer priya = customer("priya");

    @BeforeEach
    void setUp() {
        ledger = new LedgerService(accounts, transactions, new AccountAccessPolicy(), new TransactionMapper());
        lenient().when(transactions.save(any(Transaction.class))).thenAnswer(call -> call.getArgument(0));
    }

    private void exists(Account account) {
        when(accounts.findByAccountNumberForUpdate(account.getAccountNumber())).thenReturn(Optional.of(account));
    }

    // ---- deposit ----

    @Test
    void depositIncreasesBalanceAndWritesACreditEntry() {
        Account a = account("1000000001", ravi, "50000.00");
        exists(a);

        TransactionResponse result = ledger.deposit("1000000001", money("5000"), "Salary", customerAuth("ravi"));

        assertEquals(0, money("55000.00").compareTo(a.getBalance()));
        assertEquals(TransactionType.DEPOSIT, result.type());
        assertEquals(TransactionDirection.CREDIT, result.direction());
        assertEquals(TransactionStatus.SUCCESS, result.status());
        assertEquals(0, money("55000.00").compareTo(result.balanceAfter()));
        assertEquals("Salary", result.description());
    }

    @Test
    void depositRoundsToTwoDecimalsAndUsesADefaultDescription() {
        Account a = account("1000000001", ravi, "0.00");
        exists(a);

        TransactionResponse result = ledger.deposit("1000000001", money("10.005"), "  ", customerAuth("ravi"));

        assertEquals(0, money("10.01").compareTo(a.getBalance()));
        assertEquals("Deposit", result.description());
    }

    @Test
    void depositRejectsZeroAndNegativeAmountsWithoutTouchingTheBalance() {
        Account a = account("1000000001", ravi, "100.00");
        exists(a);

        assertThrows(InvalidAmountException.class, () -> ledger.deposit("1000000001", money("0"), null, customerAuth("ravi")));
        assertThrows(InvalidAmountException.class, () -> ledger.deposit("1000000001", money("-5"), null, customerAuth("ravi")));
        assertThrows(InvalidAmountException.class, () -> ledger.deposit("1000000001", null, null, customerAuth("ravi")));

        assertEquals(0, money("100.00").compareTo(a.getBalance()));
        verify(transactions, never()).save(any());
    }

    @Test
    void depositRejectsAnInactiveAccount() {
        Account a = account("1000000001", ravi, "100.00", AccountStatus.INACTIVE);
        exists(a);

        assertThrows(AccountInactiveException.class, () -> ledger.deposit("1000000001", money("10"), null, customerAuth("ravi")));
        assertEquals(0, money("100.00").compareTo(a.getBalance()));
    }

    @Test
    void depositOnSomeoneElsesAccountLooksLikeANonExistentAccount() {
        exists(account("1000000001", ravi, "100.00"));

        assertThrows(AccountNotFoundException.class, () -> ledger.deposit("1000000001", money("10"), null, customerAuth("priya")));
        verify(transactions, never()).save(any());
    }

    @Test
    void depositToAnUnknownAccountIsNotFound() {
        when(accounts.findByAccountNumberForUpdate("9999999999")).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class, () -> ledger.deposit("9999999999", money("10"), null, customerAuth("ravi")));
    }

    // ---- withdraw ----

    @Test
    void withdrawDecreasesBalanceAndWritesADebitEntry() {
        Account a = account("1000000001", ravi, "50000.00");
        exists(a);

        TransactionResponse result = ledger.withdraw("1000000001", money("10000"), null, customerAuth("ravi"));

        assertEquals(0, money("40000.00").compareTo(a.getBalance()));
        assertEquals(TransactionType.WITHDRAWAL, result.type());
        assertEquals(TransactionDirection.DEBIT, result.direction());
    }

    @Test
    void withdrawingTheWholeBalanceIsAllowedAndLeavesZero() {
        Account a = account("1000000001", ravi, "250.00");
        exists(a);

        ledger.withdraw("1000000001", money("250.00"), null, customerAuth("ravi"));

        assertEquals(0, money("0").compareTo(a.getBalance()));
    }

    @Test
    void withdrawMoreThanTheBalanceFailsAndLeavesTheBalanceUnchanged() {
        Account a = account("1000000001", ravi, "250.00");
        exists(a);

        InsufficientBalanceException ex = assertThrows(InsufficientBalanceException.class,
                () -> ledger.withdraw("1000000001", money("250.01"), null, customerAuth("ravi")));

        assertEquals("Transaction Failed: Insufficient balance.", ex.getMessage());
        assertEquals(0, money("250.00").compareTo(a.getBalance()));
        verify(transactions, never()).save(any());
    }

    @Test
    void withdrawRejectsAnInactiveAccount() {
        exists(account("1000000001", ravi, "250.00", AccountStatus.BLOCKED));

        assertThrows(AccountInactiveException.class, () -> ledger.withdraw("1000000001", money("1"), null, customerAuth("ravi")));
    }

    // ---- transfer ----

    @Test
    void transferMovesMoneyAndWritesTwoEntriesSharingOneReference() {
        Account from = account("1000000001", ravi, "50000.00");
        Account to = account("1000000002", priya, "1000.00");
        exists(from);
        exists(to);

        TransferResponse result = ledger.transfer("1000000001", "1000000002", money("5000"), "Rent", customerAuth("ravi"));

        assertEquals(0, money("45000.00").compareTo(from.getBalance()));
        assertEquals(0, money("6000.00").compareTo(to.getBalance()));
        assertEquals(0, money("45000.00").compareTo(result.fromAccountBalanceAfter()));

        ArgumentCaptor<Transaction> saved = ArgumentCaptor.forClass(Transaction.class);
        verify(transactions, org.mockito.Mockito.times(2)).save(saved.capture());
        List<Transaction> rows = saved.getAllValues();
        Transaction debit = rows.stream().filter(t -> t.getDirection() == TransactionDirection.DEBIT).findFirst().orElseThrow();
        Transaction credit = rows.stream().filter(t -> t.getDirection() == TransactionDirection.CREDIT).findFirst().orElseThrow();
        assertEquals(debit.getTransactionRef(), credit.getTransactionRef());
        assertEquals(result.transactionRef(), debit.getTransactionRef());
        assertEquals("1000000002", debit.getCounterpartyAccountNumber());
        assertEquals("1000000001", credit.getCounterpartyAccountNumber());
        assertEquals(TransactionType.TRANSFER, debit.getType());
    }

    @Test
    void transferLocksBothAccountsInAscendingNumberOrderWhicheverIsTheSource() {
        Account low = account("1000000001", priya, "1000.00");
        Account high = account("2000000002", ravi, "1000.00");
        exists(low);
        exists(high);

        // source is the HIGH number: it must still be locked second
        ledger.transfer("2000000002", "1000000001", money("10"), null, customerAuth("ravi"));

        InOrder order = inOrder(accounts);
        order.verify(accounts).findByAccountNumberForUpdate("1000000001");
        order.verify(accounts).findByAccountNumberForUpdate("2000000002");
    }

    @Test
    void transferToTheSameAccountIsRejected() {
        exists(account("1000000001", ravi, "1000.00"));

        InvalidTransactionException ex = assertThrows(InvalidTransactionException.class,
                () -> ledger.transfer("1000000001", "1000000001", money("10"), null, customerAuth("ravi")));

        assertEquals("Transaction Failed: Source and destination accounts must be different.", ex.getMessage());
    }

    @Test
    void transferToAnUnknownAccountIsNotFoundAndNothingChanges() {
        Account from = account("1000000001", ravi, "1000.00");
        exists(from);
        when(accounts.findByAccountNumberForUpdate("9999999999")).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class,
                () -> ledger.transfer("1000000001", "9999999999", money("10"), null, customerAuth("ravi")));

        assertEquals(0, money("1000.00").compareTo(from.getBalance()));
        verify(transactions, never()).save(any());
    }

    @Test
    void transferFromSomeoneElsesAccountIsNotFound() {
        Account from = account("1000000001", ravi, "1000.00");
        Account to = account("1000000002", priya, "0.00");
        exists(from);
        exists(to);

        assertThrows(AccountNotFoundException.class,
                () -> ledger.transfer("1000000001", "1000000002", money("10"), null, customerAuth("priya")));

        assertEquals(0, money("1000.00").compareTo(from.getBalance()));
        assertEquals(0, money("0.00").compareTo(to.getBalance()));
    }

    @Test
    void transferRejectsAnInactiveSourceOrDestination() {
        Account from = account("1000000001", ravi, "1000.00");
        Account inactiveTo = account("1000000002", priya, "0.00", AccountStatus.INACTIVE);
        exists(from);
        exists(inactiveTo);

        assertThrows(AccountInactiveException.class,
                () -> ledger.transfer("1000000001", "1000000002", money("10"), null, customerAuth("ravi")));
        assertEquals(0, money("1000.00").compareTo(from.getBalance()));
    }

    @Test
    void transferWithInsufficientBalanceChangesNeitherAccount() {
        Account from = account("1000000001", ravi, "100.00");
        Account to = account("1000000002", priya, "5.00");
        exists(from);
        exists(to);

        InsufficientBalanceException ex = assertThrows(InsufficientBalanceException.class,
                () -> ledger.transfer("1000000001", "1000000002", money("100.01"), null, customerAuth("ravi")));

        assertEquals("Transaction Failed: Insufficient balance in source account.", ex.getMessage());
        assertEquals(0, money("100.00").compareTo(from.getBalance()));
        assertEquals(0, money("5.00").compareTo(to.getBalance()));
        verify(transactions, never()).save(any());
    }

    @Test
    void transferRejectsAZeroAmount() {
        exists(account("1000000001", ravi, "100.00"));
        exists(account("1000000002", priya, "5.00"));

        assertThrows(InvalidAmountException.class,
                () -> ledger.transfer("1000000001", "1000000002", money("0"), null, customerAuth("ravi")));
    }
}
