package com.icc.incident.service;

import org.springframework.stereotype.Component;

/**
 * Priority from impact and urgency (DATA-MODEL). 1 is High, 3 is Low for both.
 *
 * <pre>
 *             urgency 1   urgency 2   urgency 3
 * impact 1        1           2           3
 * impact 2        2           3           4
 * impact 3        3           4           5
 * </pre>
 *
 * Every cell is impact + urgency - 1, so no table lookup is needed.
 * 1 is Critical, 2 High, 3 Moderate, 4 Low, 5 Planning.
 */
@Component
public class PriorityCalculator {

    public int calculate(int impact, int urgency) {
        return impact + urgency - 1;
    }
}
