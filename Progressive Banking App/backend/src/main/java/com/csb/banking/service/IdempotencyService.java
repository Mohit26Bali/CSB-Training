package com.csb.banking.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.csb.banking.entity.IdempotencyRecord;
import com.csb.banking.exception.IdempotencyConflictException;
import com.csb.banking.exception.InvalidIdempotencyKeyException;
import com.csb.banking.repository.IdempotencyRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * Makes money requests safe to repeat: the same {@code Idempotency-Key} executes at most once, and every repeat gets
 * the original response back (see the class comment of {@link IdempotencyRecord} for the table).
 *
 * <p>Connections: {@link TransactionService} wraps deposit / withdraw / transfer in {@link #execute}; the key comes
 * from the {@code Idempotency-Key} header read by {@code controller/TransactionController}; rows live in
 * {@code repository/IdempotencyRepository}; errors are {@code exception/InvalidIdempotencyKeyException} and
 * {@code exception/IdempotencyConflictException}.
 *
 * <p>How {@link #execute} stays correct:
 * <ol>
 *   <li><b>Replay check</b> - a known key with the same content returns the stored response; with different content
 *       it is refused (422).</li>
 *   <li><b>One transaction</b> - claim the key (insert + flush), run the money operation, store the response. The
 *       operation's own {@code @Transactional} methods join this transaction, so the key and the balance change are
 *       committed together or not at all; there is no window in which money moved but the key was lost.</li>
 *   <li><b>Simultaneous twins</b> - the second insert of the same key blocks on the database's unique index until the
 *       first transaction ends. If the first commits, the second fails with a duplicate-key error, rolls back
 *       (so its money operation never takes effect) and replays the first response. If the first rolled back, the
 *       second simply proceeds.</li>
 *   <li><b>Failures</b> - if the operation throws, the claim is rolled back with everything else, so the client may
 *       retry with the same key once the cause (e.g. insufficient funds) is fixed.</li>
 * </ol>
 * Without a key the operation just runs, as before.
 */
@Service
public class IdempotencyService {

    private static final Pattern VALID_KEY = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");
    private static final long RETENTION_HOURS = 24;

    private final IdempotencyRepository repository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public IdempotencyService(IdempotencyRepository repository, ObjectMapper objectMapper,
                              PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        // Same isolation as service/LedgerService, so its row-lock reads see other requests' committed results.
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /**
     * Runs {@code operation} once per (key, user). {@code fingerprint} must describe the request content (operation,
     * accounts, amount, ...) so that reusing a key for something else is detected.
     */
    public <T> T execute(String key, String username, String fingerprint, Class<T> responseType,
                         Supplier<T> operation) {
        if (key == null || key.isBlank()) {
            return operation.get();
        }
        if (!VALID_KEY.matcher(key).matches()) {
            throw new InvalidIdempotencyKeyException();
        }
        String hash = sha256(fingerprint);

        Optional<T> replay = findReplay(key, username, hash, responseType);
        if (replay.isPresent()) {
            return replay.get();
        }
        try {
            return transactionTemplate.execute(status -> {
                IdempotencyRecord record = repository.saveAndFlush(new IdempotencyRecord(key, username, hash));
                T result = operation.get();
                record.setResponseBody(objectMapper.writeValueAsString(result));
                repository.saveAndFlush(record);
                return result;
            });
        } catch (DataIntegrityViolationException duplicate) {
            // A twin request with the same key committed first; its transaction (and ours) are finished.
            return findReplay(key, username, hash, responseType).orElseThrow(() -> duplicate);
        }
    }

    private <T> Optional<T> findReplay(String key, String username, String hash, Class<T> responseType) {
        return repository.findByIdempotencyKeyAndUsername(key, username).map(record -> {
            if (!record.getRequestHash().equals(hash)) {
                throw new IdempotencyConflictException();
            }
            if (record.getResponseBody() == null) {
                // Cannot normally be seen: the claim is committed together with the response.
                throw new IdempotencyConflictException();
            }
            return objectMapper.readValue(record.getResponseBody(), responseType);
        });
    }

    /** Keys older than 24 hours are forgotten; a client retries within seconds or minutes, never days. */
    @Scheduled(fixedDelay = 3_600_000L, initialDelay = 600_000L)
    @Transactional
    public void purgeExpired() {
        repository.deleteOlderThan(LocalDateTime.now().minusHours(RETENTION_HOURS));
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
