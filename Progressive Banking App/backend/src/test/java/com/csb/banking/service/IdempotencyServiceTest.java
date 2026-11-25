package com.csb.banking.service;

import static com.csb.banking.service.Fixtures.money;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

import com.csb.banking.dto.TransactionResponse;
import com.csb.banking.entity.IdempotencyRecord;
import com.csb.banking.entity.TransactionDirection;
import com.csb.banking.entity.TransactionStatus;
import com.csb.banking.entity.TransactionType;
import com.csb.banking.exception.IdempotencyConflictException;
import com.csb.banking.exception.InsufficientBalanceException;
import com.csb.banking.exception.InvalidIdempotencyKeyException;
import com.csb.banking.repository.IdempotencyRepository;

import tools.jackson.databind.json.JsonMapper;

/**
 * Unit tests of {@link IdempotencyService}: run once per key, replay on repeats, refuse a key reused for something
 * else, and cope with a simultaneous twin. The repository is a small in-memory fake built on a Mockito mock (it
 * enforces the unique (key, user) constraint like the database does) and the transaction manager is a no-op mock; the
 * real database behaviour is covered by controller/IdempotencyApiTest.
 */
class IdempotencyServiceTest {

    private static final String KEY = "6f1c2d3e-1111-4222-8333-444455556666";

    /** The in-memory "table": the unique index is (key, username). */
    final Map<String, IdempotencyRecord> store = new HashMap<>();
    IdempotencyRepository repository;
    IdempotencyService service;
    final AtomicInteger runs = new AtomicInteger();
    /** When set, the first lookup answers "nothing there" although a twin request already stored the key. */
    boolean hideFirstLookup;

    private static String slot(String key, String user) {
        return key + "|" + user;
    }

    @BeforeEach
    void setUp() {
        repository = mock(IdempotencyRepository.class);
        when(repository.findByIdempotencyKeyAndUsername(any(), any())).thenAnswer(call -> {
            if (hideFirstLookup) {
                hideFirstLookup = false;
                return Optional.empty();
            }
            return Optional.ofNullable(store.get(slot(call.getArgument(0), call.getArgument(1))));
        });
        when(repository.saveAndFlush(any(IdempotencyRecord.class))).thenAnswer(call -> {
            IdempotencyRecord record = call.getArgument(0);
            String slot = slot(record.getIdempotencyKey(), record.getUsername());
            IdempotencyRecord existing = store.get(slot);
            if (existing != null && existing != record) {
                throw new DataIntegrityViolationException("duplicate key"); // the unique index
            }
            store.put(slot, record);
            return record;
        });
        service = new IdempotencyService(repository, JsonMapper.builder().build(), mock(PlatformTransactionManager.class));
    }

    private TransactionResponse response(String ref) {
        return new TransactionResponse(ref, TransactionType.DEPOSIT, TransactionDirection.CREDIT, money("10.00"),
                TransactionStatus.SUCCESS, "Deposit", null, money("110.00"), null);
    }

    private TransactionResponse run(String key, String user, String fingerprint, String ref) {
        return service.execute(key, user, fingerprint, TransactionResponse.class, () -> {
            runs.incrementAndGet();
            return response(ref);
        });
    }

    @Test
    void withoutAKeyTheOperationJustRunsEveryTime() {
        run(null, "ravi", "f", "ref-1");
        run("  ", "ravi", "f", "ref-2");

        assertEquals(2, runs.get());
        verifyNoInteractions(repository);
    }

    @Test
    void aMalformedKeyIsRejectedBeforeAnythingRuns() {
        for (String bad : new String[] { "short", "has spaces in it!", "x".repeat(65), "bad/slash-1234" }) {
            assertThrows(InvalidIdempotencyKeyException.class, () -> run(bad, "ravi", "f", "ref"));
        }
        assertEquals(0, runs.get());
    }

    @Test
    void aNewKeyRunsTheOperationOnceAndStoresTheResponse() {
        TransactionResponse result = run(KEY, "ravi", "DEPOSIT|1|10|", "ref-1");

        assertEquals("ref-1", result.transactionRef());
        assertEquals(1, runs.get());
        IdempotencyRecord stored = store.get(slot(KEY, "ravi"));
        assertTrue(stored.getResponseBody().contains("\"transactionRef\":\"ref-1\""));
    }

    @Test
    void aRepeatWithTheSameKeyAndRequestReplaysTheFirstResponseWithoutRunning() {
        run(KEY, "ravi", "DEPOSIT|1|10|", "ref-1");

        TransactionResponse replay = run(KEY, "ravi", "DEPOSIT|1|10|", "ref-SECOND");

        assertEquals("ref-1", replay.transactionRef());
        assertEquals(0, money("10.00").compareTo(replay.amount()));
        assertEquals(1, runs.get());
    }

    @Test
    void theSameKeyForADifferentRequestIsAConflict() {
        run(KEY, "ravi", "DEPOSIT|1|10|", "ref-1");

        assertThrows(IdempotencyConflictException.class, () -> run(KEY, "ravi", "DEPOSIT|1|20|", "ref-2"));
        assertEquals(1, runs.get());
    }

    @Test
    void keysAreScopedPerUser() {
        run(KEY, "ravi", "DEPOSIT|1|10|", "ref-ravi");
        TransactionResponse other = run(KEY, "priya", "DEPOSIT|1|10|", "ref-priya");

        assertEquals("ref-priya", other.transactionRef());
        assertEquals(2, runs.get());
    }

    @Test
    void aSimultaneousTwinThatCommittedFirstIsReplayedInsteadOfBeingRunAgain() {
        // The twin already stored the key; our first lookup raced ahead of it and saw nothing.
        run(KEY, "ravi", "DEPOSIT|1|10|", "ref-twin");
        runs.set(0);
        hideFirstLookup = true;

        // Our own claim collides with the twin's row (unique index), so we fall back to replaying the twin's response.
        TransactionResponse result = run(KEY, "ravi", "DEPOSIT|1|10|", "ref-ours");

        assertEquals("ref-twin", result.transactionRef());
        assertEquals(1, store.size());
    }

    @Test
    void aFailedOperationPropagatesItsErrorAndLeavesNoResponseStored() {
        assertThrows(InsufficientBalanceException.class, () -> service.execute(KEY, "ravi", "f", TransactionResponse.class, () -> {
            throw new InsufficientBalanceException("no funds");
        }));

        // The claim row was inserted before the operation; in production its transaction rolls back with the error.
        // Here the transaction manager is a mock, so we only assert that no response was ever recorded for it.
        store.values().forEach(r -> assertNull(r.getResponseBody()));
    }

    @Test
    void theReplayIsAFreshObjectNotTheOriginalInstance() {
        TransactionResponse first = run(KEY, "ravi", "f", "ref-1");
        TransactionResponse replay = run(KEY, "ravi", "f", "ref-2");

        assertEquals(first.transactionRef(), replay.transactionRef());
        assertEquals(first.amount().compareTo(replay.amount()), 0);
        // deserialised from the stored JSON, so a different instance
        assertNotSame(first, replay);
    }
}
