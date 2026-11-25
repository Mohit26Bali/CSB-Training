package com.csb.banking.dto;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.csb.banking.exception.InvalidFilterException;

/** Unit tests of the consistency rules built into {@link TransactionFilter}. */
class TransactionFilterTest {

    private static final LocalDate D1 = LocalDate.of(2026, 2, 1);
    private static final LocalDate D2 = LocalDate.of(2026, 2, 28);

    private TransactionFilter amounts(String min, String max) {
        return new TransactionFilter(null, null, null, null, null,
                min == null ? null : new BigDecimal(min), max == null ? null : new BigDecimal(max));
    }

    @Test
    void anEmptyFilterIsValid() {
        assertDoesNotThrow(TransactionFilter::none);
    }

    @Test
    void aDateRangeMayBeASingleDay() {
        assertDoesNotThrow(() -> new TransactionFilter(D1, D1, null, null, null, null, null));
    }

    @Test
    void fromAfterToIsRejected() {
        InvalidFilterException ex = assertThrows(InvalidFilterException.class,
                () -> new TransactionFilter(D2, D1, null, null, null, null, null));
        assertEquals("'from' date must not be after 'to' date", ex.getMessage());
    }

    @Test
    void minAboveMaxIsRejected() {
        assertThrows(InvalidFilterException.class, () -> amounts("500", "100"));
    }

    @Test
    void equalMinAndMaxAreAllowed() {
        assertDoesNotThrow(() -> amounts("100", "100"));
    }

    @Test
    void negativeBoundsAreRejected() {
        assertThrows(InvalidFilterException.class, () -> amounts("-1", null));
        assertThrows(InvalidFilterException.class, () -> amounts(null, "-0.01"));
    }

    @Test
    void invalidFilterErrorsAreNotAuditableAs400() {
        InvalidFilterException ex = new InvalidFilterException("x");
        assertEquals(400, ex.getStatus().value());
        assertEquals(false, ex.isAuditable());
    }
}
