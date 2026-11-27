package com.hackathon.scholarship.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.hackathon.scholarship.entity.ScholarshipProgram;

/**
 * The eligibility score (0 to 100) shown to the admin:
 * <ul>
 *   <li>academics: up to 60 points, CGPA / 10 of them</li>
 *   <li>need: up to 40 points, falling in a straight line from 40 at zero income to 0 at the programme's income ceiling</li>
 * </ul>
 * "Meets criteria" is separate: CGPA at or above the programme minimum and income at or below its ceiling.
 */
public final class EligibilityCalculator {

    private EligibilityCalculator() {
    }

    public static int score(BigDecimal gpa, BigDecimal income, ScholarshipProgram program) {
        double academics = gpa.doubleValue() / 10.0 * 60.0;
        double ceiling = program.getMaxFamilyIncome().doubleValue();
        double need = income.doubleValue() >= ceiling ? 0 : (1 - income.doubleValue() / ceiling) * 40.0;
        return (int) Math.round(Math.min(100, academics + need));
    }

    public static boolean meetsCriteria(BigDecimal gpa, BigDecimal income, ScholarshipProgram program) {
        return gpa.compareTo(program.getMinGpa()) >= 0
                && income.setScale(2, RoundingMode.HALF_UP).compareTo(program.getMaxFamilyIncome()) <= 0;
    }
}
