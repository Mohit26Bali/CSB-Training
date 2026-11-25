package com.csb.banking.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;

import com.csb.banking.entity.Account;

/**
 * Tests the {@code Idempotency-Key} header on the money endpoints: repeats are replayed instead of executed again,
 * a key cannot be reused for a different request, keys are per user, failed attempts do not burn the key, and
 * simultaneous duplicates still move money once. Fixture from {@link AbstractApiTest}.
 */
class IdempotencyApiTest extends AbstractApiTest {

    private static final String KEY = "6f1c2d3e-1111-4222-8333-444455556666";

    private ResultActions send(String url, String token, String key, String json) throws Exception {
        var request = post(url).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(json);
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mvc.perform(request);
    }

    private String deposit() {
        return "/api/accounts/" + raviAccountNumber + "/deposit";
    }

    private String withdraw() {
        return "/api/accounts/" + raviAccountNumber + "/withdraw";
    }

    @Test
    void repeatedDepositWithSameKeyIsAppliedOnceAndReplaysTheFirstResponse() throws Exception {
        String first = send(deposit(), raviToken, KEY, "{\"amount\":1000}")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String second = send(deposit(), raviToken, KEY, "{\"amount\":1000}")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertEquals(first, second);
        assertEquals(0, new BigDecimal("51000.00").compareTo(balanceOf(raviAccountNumber)));
        assertEquals(1, transactionRepository.count());
    }

    @Test
    void withoutKeyEveryRequestIsExecuted() throws Exception {
        send(deposit(), raviToken, null, "{\"amount\":1000}").andExpect(status().isOk());
        send(deposit(), raviToken, null, "{\"amount\":1000}").andExpect(status().isOk());
        assertEquals(0, new BigDecimal("52000.00").compareTo(balanceOf(raviAccountNumber)));
    }

    @Test
    void repeatedTransferReplaysWith201AndMovesMoneyOnce() throws Exception {
        newAccount(priya, "1000000002", "0.00");
        String body = "{\"fromAccount\":\"" + raviAccountNumber + "\",\"toAccount\":\"1000000002\",\"amount\":700}";

        String first = send("/api/transfers", raviToken, KEY, body)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String second = send("/api/transfers", raviToken, KEY, body)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        assertEquals(first, second);
        assertEquals(0, new BigDecimal("49300.00").compareTo(balanceOf(raviAccountNumber)));
        assertEquals(0, new BigDecimal("700.00").compareTo(balanceOf("1000000002")));
        assertEquals(2, transactionRepository.count());
    }

    @Test
    void reusingAKeyForADifferentRequestIsRejected() throws Exception {
        send(deposit(), raviToken, KEY, "{\"amount\":1000}").andExpect(status().isOk());
        send(deposit(), raviToken, KEY, "{\"amount\":2000}").andExpect(status().isUnprocessableContent());
        send(withdraw(), raviToken, KEY, "{\"amount\":1000}").andExpect(status().isUnprocessableContent());
        assertEquals(0, new BigDecimal("51000.00").compareTo(balanceOf(raviAccountNumber)));
        // Only the one real deposit is in the ledger: key misuse is not a failed transaction.
        assertEquals(1, transactionRepository.count());
    }

    @Test
    void malformedKeyIsRejectedWith400() throws Exception {
        for (String bad : new String[] { "short", "has spaces in it!", "x".repeat(65) }) {
            send(deposit(), raviToken, bad, "{\"amount\":1000}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Idempotency-Key")));
        }
        assertEquals(0, new BigDecimal("50000.00").compareTo(balanceOf(raviAccountNumber)));
        assertEquals(0, transactionRepository.count());
    }

    @Test
    void sameKeyFromDifferentUsersDoesNotCollide() throws Exception {
        Account priyaAccount = newAccount(priya, "1000000002", "100.00");
        send(deposit(), raviToken, KEY, "{\"amount\":1000}").andExpect(status().isOk());
        send("/api/accounts/" + priyaAccount.getAccountNumber() + "/deposit", priyaToken, KEY, "{\"amount\":1000}")
                .andExpect(status().isOk());
        assertEquals(0, new BigDecimal("51000.00").compareTo(balanceOf(raviAccountNumber)));
        assertEquals(0, new BigDecimal("1100.00").compareTo(balanceOf("1000000002")));
    }

    @Test
    void failedAttemptDoesNotBurnTheKey() throws Exception {
        send(withdraw(), raviToken, KEY, "{\"amount\":60000}").andExpect(status().isUnprocessableContent());
        send(deposit(), raviToken, null, "{\"amount\":20000}").andExpect(status().isOk());
        // Same key, same request: now it can succeed because the earlier failure left no trace behind the key.
        send(withdraw(), raviToken, KEY, "{\"amount\":60000}").andExpect(status().isOk());
        assertEquals(0, new BigDecimal("10000.00").compareTo(balanceOf(raviAccountNumber)));
    }

    @Test
    void simultaneousDuplicatesMoveMoneyOnce() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<MockHttpServletResponse>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<MockHttpServletResponse> task = () ->
                    send(withdraw(), raviToken, KEY, "{\"amount\":100}").andReturn().getResponse();
            results.add(pool.submit(task));
        }
        Set<String> bodies = new HashSet<>();
        for (Future<MockHttpServletResponse> f : results) {
            MockHttpServletResponse response = f.get();
            assertEquals(200, response.getStatus());
            bodies.add(response.getContentAsString());
        }
        pool.shutdown();

        assertEquals(1, bodies.size());
        assertEquals(0, new BigDecimal("49900.00").compareTo(balanceOf(raviAccountNumber)));
        assertEquals(1, transactionRepository.count());
    }
}
