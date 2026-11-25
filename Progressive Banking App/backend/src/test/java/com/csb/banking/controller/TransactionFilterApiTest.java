package com.csb.banking.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Tests the history filters (date range, type, status, direction, amount range) end to end. Ledger rows are created
 * through the real API and then back-dated directly in the database (created_at is set by the application and cannot
 * be chosen through the API), giving a known history for ravi's account 1000000001 (opening balance 50 000.00,
 * which itself writes no ledger row in this fixture):
 *
 * <pre>
 *  1  2026-01-10  DEPOSIT     CREDIT  1 000.00  SUCCESS
 *  2  2026-02-15  WITHDRAWAL  DEBIT     500.00  SUCCESS
 *  3  2026-02-20  TRANSFER    DEBIT   2 000.00  SUCCESS   (to priya)
 *  4  2026-03-05  WITHDRAWAL  DEBIT  90 000.00  FAILED    (insufficient balance)
 * </pre>
 */
class TransactionFilterApiTest extends AbstractApiTest {

    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void history() throws Exception {
        newAccount(priya, "1000000002", "0.00");
        send("/api/accounts/" + raviAccountNumber + "/deposit", "{\"amount\":1000}");
        send("/api/accounts/" + raviAccountNumber + "/withdraw", "{\"amount\":500}");
        send("/api/transfers", "{\"fromAccount\":\"" + raviAccountNumber + "\",\"toAccount\":\"1000000002\",\"amount\":2000}");
        send("/api/accounts/" + raviAccountNumber + "/withdraw", "{\"amount\":90000}"); // rejected -> FAILED row

        // Back-date the four ravi rows, in the order they were created.
        var ids = jdbc.queryForList("select t.id from transactions t join accounts a on a.id = t.account_id "
                + "where a.account_number = ? order by t.id", Long.class, raviAccountNumber);
        String[] days = { "2026-01-10T09:00:00", "2026-02-15T12:00:00", "2026-02-20T23:59:59", "2026-03-05T08:30:00" };
        for (int i = 0; i < ids.size(); i++) {
            jdbc.update("update transactions set created_at = ? where id = ?",
                    Timestamp.valueOf(LocalDateTime.parse(days[i])), ids.get(i));
        }
    }

    private void send(String url, String json) throws Exception {
        mvc.perform(post(url).header("Authorization", raviToken).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions history(String query) throws Exception {
        return mvc.perform(get("/api/accounts/" + raviAccountNumber + "/transactions" + query)
                .header("Authorization", raviToken));
    }

    @Test
    void noFilterReturnsEverythingNewestFirst() throws Exception {
        history("").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[0].status").value("FAILED"))
                .andExpect(jsonPath("$.content[3].type").value("DEPOSIT"));
    }

    @Test
    void filtersByType() throws Exception {
        history("?type=WITHDRAWAL").andExpect(jsonPath("$.totalElements").value(2));
        history("?type=TRANSFER").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].counterpartyAccountNumber").value("1000000002"));
        history("?type=DEPOSIT").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void filtersByStatusAndDirection() throws Exception {
        history("?status=FAILED").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].amount").value(90000.00));
        history("?status=SUCCESS").andExpect(jsonPath("$.totalElements").value(3));
        history("?direction=CREDIT").andExpect(jsonPath("$.totalElements").value(1));
        history("?direction=DEBIT").andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void filtersByAmountRangeInclusive() throws Exception {
        history("?minAmount=500&maxAmount=2000").andExpect(jsonPath("$.totalElements").value(3));
        history("?minAmount=2000").andExpect(jsonPath("$.totalElements").value(2)); // 2 000 and 90 000
        history("?maxAmount=999.99").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void dateRangeIsInclusiveOfBothEndDays() throws Exception {
        // 2026-02-20T23:59:59 must still be inside "to=2026-02-20".
        history("?from=2026-02-15&to=2026-02-20").andExpect(jsonPath("$.totalElements").value(2));
        history("?from=2026-02-16&to=2026-02-20").andExpect(jsonPath("$.totalElements").value(1));
        history("?from=2026-03-01").andExpect(jsonPath("$.totalElements").value(1));
        history("?to=2026-01-31").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void filtersCombineWithAnd() throws Exception {
        history("?type=WITHDRAWAL&status=SUCCESS&from=2026-02-01&to=2026-02-28&maxAmount=1000")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].amount").value(500.00));
        history("?type=DEPOSIT&status=FAILED").andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void filtersRespectPagingAndTotals() throws Exception {
        history("?direction=DEBIT&size=2&page=0").andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(2));
        history("?direction=DEBIT&size=2&page=1").andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void invalidFiltersAreRejectedWith400() throws Exception {
        history("?from=2026-03-01&to=2026-02-01").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("'from' date must not be after 'to' date"));
        history("?minAmount=500&maxAmount=100").andExpect(status().isBadRequest());
        history("?minAmount=-1").andExpect(status().isBadRequest());
        history("?type=LOAN").andExpect(status().isBadRequest());
        history("?from=yesterday").andExpect(status().isBadRequest());
    }

    @Test
    void filtersNeverWidenAccess() throws Exception {
        // Another customer still gets 404 whatever the filter; an admin can read.
        mvc.perform(get("/api/accounts/" + raviAccountNumber + "/transactions?type=DEPOSIT")
                        .header("Authorization", priyaToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/accounts/" + raviAccountNumber + "/transactions?type=DEPOSIT")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }
}
