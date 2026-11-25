package com.csb.banking.security;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Runs {@link PasswordPolicy#problem} for fields marked {@link StrongPassword} and reports its message as the violation.
 * Blank and badly sized values pass here on purpose: {@code @NotBlank} and {@code @Size} already report those, and one field
 * should not show two messages for the same mistake.
 */
public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        String problem = PasswordPolicy.problem(value);
        if (problem == null) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(problem).addConstraintViolation();
        return false;
    }
}
