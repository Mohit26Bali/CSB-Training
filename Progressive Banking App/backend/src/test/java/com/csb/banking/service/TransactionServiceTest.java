package com.csb.banking.service;

import static com.csb.banking.service.Fixtures.account;
import static com.csb.banking.service.Fixtures.adminAuth;
import static com.csb.banking.service.Fixtures.customer;
import static com.csb.banking.service.Fixtures.customerAuth;
import static com.csb.banking.service.Fixtures.money;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.csb.banking.dto.AmountRequest;
import com.csb.banking.dto.PageResponse;
import com.csb.banking.dto.TransactionFilter;
import com.csb.banking.dto.TransactionResponse;
import com.csb.banking.dto.TransferRequest;
import com.csb.banking.entity.Account;
import com.csb.banking.entity.Customer;
import com.csb.banking.entity.Transaction;
import com.csb.banking.entity.TransactionDirection;
import com.csb.banking.entity.TransactionStatus;
import com.csb.banking.entity.TransactionType;
import com.csb.banking.exception.AccountNotFoundException;
import com.csb.banking.exception.IdempotencyConflictException;
import com.csb.banking.exception.InsufficientBalanceException;
import com.csb.banking.mapper.TransactionMapper;
import com.csb.banking.repository.AccountRepository;
import com.csb.banking.repository.TransactionRepository;

/**
 * Unit tests of the orchestration in {@link TransactionService}: which rejected operations get a FAILED ledger row,
 * that the idempotency layer wraps the ledger call, and how history paging is bounded. The ledger, audit and
 * idempotency services are mocks, so only this class's own decisions are tested.
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock LedgerService ledger;
    @Mock TransactionAuditService audit;
    @Mock IdempotencyService idempotency;
    @Mock AccountRepository accounts;
    @Mock TransactionRepository transactions;

    TransactionService service;
    Customer ravi = customer("ravi");

    @BeforeEach
    void setUp() {
        service = new TransactionService(ledger, audit, accounts, transactions, new AccountAccessPolicy(),
                new TransactionMapper(), idempotency);
    }

    /** Makes the mocked idempotency layer behave as "no key / first execution": it just runs the operation. */
    @SuppressWarnings("unchecked")
    private void idempotencyRunsOperation() {
        when(idempotency.execute(any(), anyString(), anyString(), any(), any())).thenAnswer(call ->
                ((Supplier<Object>) call.getArgument(4)).get());
    }

    private TransactionResponse response() {
        return new TransactionResponse("ref", TransactionType.WITHDRAWAL, TransactionDirection.DEBIT, money("10.00"),
                TransactionStatus.SUCCESS, null, null, money("90.00"), null);
    }

    @Test
    void successfulWithdrawalIsNotAudited() {
        idempotencyRunsOperation();
        TransactionResponse expected = response();
        when(ledger.withdraw("1000000001", money("10"), "rent", customerAuth("ravi"))).thenReturn(expected);

        TransactionResponse actual = service.withdraw("1000000001", new AmountRequest(money("10"), "rent"), "key-123456", customerAuth("ravi"));

        assertSame(expected, actual);
        verifyNoInteractions(audit);
    }

    @Test
    void rejectedWithdrawalIsAuditedOnTheCallersAccountAndTheErrorIsRethrown() {
        idempotencyRunsOperation();
        when(ledger.withdraw(any(), any(), any(), any())).thenThrow(new InsufficientBalanceException("Transaction Failed: Insufficient balance."));

        InsufficientBalanceException ex = assertThrows(InsufficientBalanceException.class,
                () -> service.withdraw("1000000001", new AmountRequest(money("999"), null), null, customerAuth("ravi")));

        assertEquals("Transaction Failed: Insufficient balance.", ex.getMessage());
        verify(audit).recordFailure(eq("1000000001"), isNull(), eq(TransactionType.WITHDRAWAL),
                eq(TransactionDirection.DEBIT), eq(money("999.00")), eq("Transaction Failed: Insufficient balance."));
    }

    @Test
    void rejectedTransferIsAuditedOnTheSourceAccountWithTheCounterparty() {
        idempotencyRunsOperation();
        when(ledger.transfer(any(), any(), any(), any(), any())).thenThrow(new InsufficientBalanceException("no funds"));

        assertThrows(InsufficientBalanceException.class, () -> service.transfer(
                new TransferRequest("1000000001", "1000000002", money("500"), null), null, customerAuth("ravi")));

        verify(audit).recordFailure("1000000001", "1000000002", TransactionType.TRANSFER, TransactionDirection.DEBIT,
                money("500.00"), "no funds");
    }

    @Test
    void notFoundIsNeverAudited() {
        idempotencyRunsOperation();
        when(ledger.deposit(any(), any(), any(), any())).thenThrow(new AccountNotFoundException("1000000001"));

        assertThrows(AccountNotFoundException.class,
                () -> service.deposit("1000000001", new AmountRequest(money("10"), null), null, customerAuth("priya")));

        verifyNoInteractions(audit);
    }

    @Test
    void idempotencyKeyMisuseIsNotAuditedEither() {
        when(idempotency.execute(any(), anyString(), anyString(), any(), any())).thenThrow(new IdempotencyConflictException());

        assertThrows(IdempotencyConflictException.class,
                () -> service.deposit("1000000001", new AmountRequest(money("10"), null), "key-123456", customerAuth("ravi")));

        verifyNoInteractions(audit);
    }

    @Test
    void theKeyAndAFingerprintOfTheRequestArePassedToTheIdempotencyLayer() {
        idempotencyRunsOperation();
        when(ledger.deposit(any(), any(), any(), any())).thenReturn(response());

        service.deposit("1000000001", new AmountRequest(money("1000.00"), " Salary "), "key-123456", customerAuth("ravi"));

        // amount is normalised (1000.00 -> 1000) and the description trimmed, so equal requests give equal fingerprints
        verify(idempotency).execute(eq("key-123456"), eq("ravi"), eq("DEPOSIT|1000000001|1000|Salary"),
                eq(TransactionResponse.class), any());
    }

    // ---- history ----

    @SuppressWarnings("unchecked")
    @Test
    void historyClampsPageAndSizeAndSortsNewestFirst() {
        Account a = account("1000000001", ravi, "0.00");
        when(accounts.findByAccountNumber("1000000001")).thenReturn(Optional.of(a));
        Page<Transaction> empty = new PageImpl<>(List.of());
        when(transactions.findAll(any(Specification.class), any(Pageable.class))).thenReturn(empty);

        PageResponse<TransactionResponse> result = service.getHistory("1000000001", -5, 100000, TransactionFilter.none(), customerAuth("ravi"));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(transactions).findAll(any(Specification.class), pageable.capture());
        assertEquals(0, pageable.getValue().getPageNumber());
        assertEquals(100, pageable.getValue().getPageSize());
        assertEquals("createdAt: DESC,id: DESC", pageable.getValue().getSort().toString());
        assertEquals(0, result.totalElements());
    }

    @Test
    void historyOfSomeoneElsesAccountIsNotFoundForACustomerButReadableForAnAdmin() {
        Account a = account("1000000001", ravi, "0.00");
        when(accounts.findByAccountNumber("1000000001")).thenReturn(Optional.of(a));

        assertThrows(AccountNotFoundException.class,
                () -> service.getHistory("1000000001", 0, 10, TransactionFilter.none(), customerAuth("priya")));
        verify(transactions, never()).findAll(any(Specification.class), any(Pageable.class));

        when(transactions.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        service.getHistory("1000000001", 0, 10, TransactionFilter.none(), adminAuth());
        verify(transactions).findAll(any(Specification.class), any(Pageable.class));
    }
}
