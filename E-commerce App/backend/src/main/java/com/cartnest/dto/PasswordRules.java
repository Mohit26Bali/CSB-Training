package com.cartnest.dto;

/** The password policy, shared by registration and password change. */
public final class PasswordRules {

    /** 8 to 64 characters with at least one letter and one digit. */
    public static final String PATTERN = "(?=.*[A-Za-z])(?=.*\\d).{8,64}";
    public static final String MESSAGE = "Password must be 8 to 64 characters with at least one letter and one digit";

    private PasswordRules() {
    }
}
