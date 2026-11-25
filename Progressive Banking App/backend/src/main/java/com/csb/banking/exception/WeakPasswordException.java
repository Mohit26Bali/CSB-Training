package com.csb.banking.exception;

import org.springframework.http.HttpStatus;

/**
 * A chosen password breaks the rule that needs the rest of the registration form: it contains the person's own username or
 * email name (400). Thrown by {@code service/AuthService#register} using {@code security/PasswordPolicy#identityProblem}.
 * The context-free strength rules are checked earlier, on the request itself, by {@code security/StrongPassword}.
 */
public class WeakPasswordException extends BankingException {

    public WeakPasswordException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }

    /** Registration is not a transaction attempt: nothing to record in the ledger. */
    @Override
    public boolean isAuditable() {
        return false;
    }
}
