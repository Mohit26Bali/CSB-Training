package com.icc.incident.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PriorityCalculatorTest {

    private final PriorityCalculator calculator = new PriorityCalculator();

    // The matrix from DATA-MODEL: impact, urgency, expected priority.
    @ParameterizedTest
    @CsvSource({
            "1, 1, 1",
            "1, 2, 2", "2, 1, 2",
            "1, 3, 3", "2, 2, 3", "3, 1, 3",
            "2, 3, 4", "3, 2, 4",
            "3, 3, 5"
    })
    void priority_followsTheMatrix(int impact, int urgency, int expected) {
        assertEquals(expected, calculator.calculate(impact, urgency));
    }
}
