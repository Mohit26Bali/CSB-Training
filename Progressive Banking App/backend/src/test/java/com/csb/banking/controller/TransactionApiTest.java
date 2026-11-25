package com.csb.banking.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.csb.banking.entity.Account;
import com.csb.banking.entity.AccountStatus;

/**
 * Money-movement tests: deposit, withdraw, transfer and history, including the negative scenarios of section 20 of
 * the project description, the FAILED audit rows, ownership, and concurrent withdrawals. Fixture: ravi owns
 * 1000000001 (50 000.00); tests add accounts as needed (see {@link AbstractApiTest}).
 */
class TransactionApiTest extends AbstractApiTest {

    private ResultActions send(String url, String token, String json) throws Exception {
        return mvc.perform(post(url).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String deposit(String account) {
        return "/api/accounts/" + account + "/deposit";
    }

    private String withdraw(String account) {
        return "/api/accounts/" + account + "/withdraw";
    }

    private String transfer(String from, String to, String amount) {
        return "{\"fromAccount\":\"" + from + "\",\"toAccount\":\"" + to + "\",\"amount\":" + amount + "}";
    }

    // ---- deposit ----

    @Test
    void depositIncreasesBalanceAndAppearsInHistory() throws Exception {
        send(deposit(raviAccountNumber), raviToken, "{\"amount\":5000,\"description\":\"Salary\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.direction").value("CREDIT"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.balanceAfter").value(55000.00))
                .andExpect(jsonPath("$.transactionRef").isNotEmpty());
        assertEquals(0, new BigDecimal("55000.00").compareTo(balanceOf(raviAccountNumber)));

        mvc.perform(get("/api/accounts/" + raviAccountNumber + "/transactions").header("Authorization", raviToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].description").value("Salary"));
    }

    @Test
    void zeroNegativeOrMissingAmountIsRejectedAndBalanceUnchanged() throws Exception {
        for (String body : new String[] { "{\"amount\":0}", "{\"amount\":-10}", "{}", "{\"amount\":1.234}" }) {
            send(deposit(raviAccountNumber), raviToken, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.amount").exists());
        }
        assertEquals(0, new BigDecimal("50000.00").compareTo(balanceOf(raviAccountNumber)));
    }

    @Test
    void depositToInactiveAccountIsRejectedAndAudited() throws Exception {
        Account account = accountRepository.findByAccountNumber(raviAccountNumber).orElseThrow();
        account.setStatus(AccountStatus.INACTIVE);
        accountRepository.save(account);

        send(deposit(raviAccountNumber), raviToken, "{\"amount\":100}")
                .andExpect(status().isUnprocessableEntity());
        assertEquals(0, new BigDecimal("50000.00").compareTo(balanceOf(raviAccountNumber)));
        mvc.perform(get("/api/accounts/" + raviAccountNumber + "/transactions").header("Authorization", raviToken))
                .andExpect(jsonPath("$.content[0].status").value("FAILED"));
    }

    // ---- withdraw ----

    @Test
    void withdrawWithinBalanceDecreasesIt() throws Exception {
        send(withdraw(raviAccountNumber), raviToken, "{\"amount\":10000}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.direction").value("DEBIT"))
                .andExpect(jsonPath("$.balanceAfter").value(40000.00));
        assertEquals(0, new BigDecimal("40000.00").compareTo(balanceOf(raviAccountNumber)));
    }

    @Test
    void withdrawBeyondBalanceIsRejectedBalanceUnchangedAndFailureRecorded() throws Exception {
        send(withdraw(raviAccountNumber), raviToken, "{\"amount\":50000.01}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Transaction Failed: Insufficient balance."));
        assertEquals(0, new BigDecimal("50000.00").compareTo(balanceOf(raviAccountNumber)));

        mvc.perform(get("/api/accounts/" + raviAccountNumber + "/transactions").header("Authorization", raviToken))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("FAILED"))
                .andExpect(jsonPath("$.content[0].type").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.content[0].balanceAfter").value(50000.00));
    }

    // ---- ownership / roles ----

    @Test
    void cannotMoveMoneyOnSomeoneElsesAccountAndNothingIsRecorded() throws Exception {
        send(deposit(raviAccountNumber), priyaToken, "{\"amount\":100}").andExpect(status().isNotFound());
        send(withdraw(raviAccountNumber), priyaToken, "{\"amount\":100}").andExpect(status().isNotFound());
        assertEquals(0, new BigDecimal("50000.00").compareTo(balanceOf(raviAccountNumber)));
        assertEquals(0, transactionRepository.count());
    }

    @Test
    void adminCannotMoveMoneyButCanReadHistory() throws Exception {
        send(deposit(raviAccountNumber), adminToken, "{\"amount\":100}").andExpect(status().isForbidden());
        mvc.perform(get("/api/accounts/" + raviAccountNumber + "/transactions").header("Authorization", adminToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/accounts/" + raviAccountNumber + "/transactions").header("Authorization", priyaToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/accounts/" + raviAccountNumber + "/transactions"))
                .andExpect(status().isUnauthorized());
    }

    // ---- transfer ----

    @Test
    void transferMovesMoneyAndWritesTwoLedgerRowsWithOneReference() throws Exception {
        Account priyaAccount = newAccount(priya, "1000000002", "1000.00");

        send("/api/transfers", raviToken, transfer(raviAccountNumber, "1000000002", "5000"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.fromAccountBalanceAfter").value(45000.00))
                .andExpect(jsonPath("$.transactionRef").isNotEmpty());

        assertEquals(0, new BigDecimal("45000.00").compareTo(balanceOf(raviAccountNumber)));
        assertEquals(0, new BigDecimal("6000.00").compareTo(balanceOf(priyaAccount.getAccountNumber())));

        mvc.perform(get("/api/accounts/" + raviAccountNumber + "/transactions").header("Authorization", raviToken))
                .andExpect(jsonPath("$.content[0].direction").value("DEBIT"))
                .andExpect(jsonPath("$.content[0].counterpartyAccountNumber").value("1000000002"));
        mvc.perform(get("/api/accounts/1000000002/transactions").header("Authorization", priyaToken))
                .andExpect(jsonPath("$.content[0].direction").value("CREDIT"))
                .andExpect(jsonPath("$.content[0].counterpartyAccountNumber").value(raviAccountNumber));
        assertEquals(1, transactionRepository.findAll().stream()
                .map(t -> t.getTransactionRef()).distinct().count());
    }

    @Test
    void transferRuleViolationsAreRejectedWithoutChangingAnyBalance() throws Exception {
        Account priyaAccount = newAccount(priya, "1000000002", "1000.00");
        Account blocked = newAccount(priya, "1000000003", "10.00");
        blocked.setStatus(AccountStatus.BLOCKED);
        accountRepository.save(blocked);

        // same account
        send("/api/transfers", raviToken, transfer(raviAccountNumber, raviAccountNumber, "10"))
                .andExpect(status().isUnprocessableEntity());
        // unknown destination
        send("/api/transfers", raviToken, transfer(raviAccountNumber, "9999999999", "10"))
                .andExpect(status().isNotFound());
        // inactive destination
        send("/api/transfers", raviToken, transfer(raviAccountNumber, "1000000003", "10"))
                .andExpect(status().isUnprocessableEntity());
        // insufficient balance
        send("/api/transfers", raviToken, transfer(raviAccountNumber, "1000000002", "50000.01"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Transaction Failed: Insufficient balance in source account."));
        // zero amount
        send("/api/transfers", raviToken, transfer(raviAccountNumber, "1000000002", "0"))
                .andExpect(status().isBadRequest());
        // source is somebody else's account
        send("/api/transfers", priyaToken, transfer(raviAccountNumber, "1000000002", "10"))
                .andExpect(status().isNotFound());
        // admin cannot transfer
        send("/api/transfers", adminToken, transfer(raviAccountNumber, "1000000002", "10"))
                .andExpect(status().isForbidden());

        assertEquals(0, new BigDecimal("50000.00").compareTo(balanceOf(raviAccountNumber)));
        assertEquals(0, new BigDecimal("1000.00").compareTo(balanceOf(priyaAccount.getAccountNumber())));
        // FAILED rows exist only for ravi's own rejected attempts: same account, inactive destination, no funds.
        assertEquals(3, transactionRepository.findAll().stream()
                .filter(t -> t.getStatus().name().equals("FAILED")).count());
    }

    // ---- history paging ----

    @Test
    void historyIsNewestFirstAndPaged() throws Exception {
        for (int i = 1; i <= 3; i++) {
            send(deposit(raviAccountNumber), raviToken, "{\"amount\":" + i + "00}").andExpect(status().isOk());
        }
        mvc.perform(get("/api/accounts/" + raviAccountNumber + "/transactions?page=0&size=2")
                        .header("Authorization", raviToken))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].amount").value(300.00))
                .andExpect(jsonPath("$.content[1].amount").value(200.00))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/accounts/" + raviAccountNumber + "/transactions?page=1&size=2")
                        .header("Authorization", raviToken))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].amount").value(100.00));
    }

    // ---- concurrency ----

    @Test
    void parallelWithdrawalsNeverOverdrawTheAccount() throws Exception {
        Account account = newAccount(ravi, "1000000009", "500.00");
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<Integer> task = () -> mvc.perform(
                            post(withdraw(account.getAccountNumber())).header("Authorization", raviToken)
                                    .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":100}"))
                    .andReturn().getResponse().getStatus();
            results.add(pool.submit(task));
        }
        long succeeded = 0;
        for (Future<Integer> f : results) {
            if (f.get() == 200) {
                succeeded++;
            }
        }
        pool.shutdown();

        assertEquals(5, succeeded);
        assertEquals(0, BigDecimal.ZERO.compareTo(balanceOf(account.getAccountNumber())));
    }
}
