package com.csb.banking.service;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.csb.banking.entity.Account;
import com.csb.banking.exception.AccountNotFoundException;

/**
 * The single definition of "who may touch which account" (project description, section 19).
 *
 * <ul>
 *   <li><b>Read access:</b> the owning customer or any ADMIN.</li>
 *   <li><b>Owner access</b> (moving money): the owning customer only; administrators cannot deposit, withdraw or
 *       transfer on behalf of customers.</li>
 * </ul>
 * A refusal throws {@link AccountNotFoundException} rather than a 403, so the API never confirms that someone else's
 * account number exists. Shared by {@link AccountService}, {@link LedgerService} and {@link TransactionService}; call
 * it inside a transaction because it reads the lazily loaded account -> customer -> user chain.
 */
@Component
public class AccountAccessPolicy {

    public void requireReadAccess(Account account, Authentication authentication) {
        if (!isAdmin(authentication) && !isOwner(account, authentication)) {
            throw new AccountNotFoundException(account.getAccountNumber());
        }
    }

    public void requireOwner(Account account, Authentication authentication) {
        if (!isOwner(account, authentication)) {
            throw new AccountNotFoundException(account.getAccountNumber());
        }
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }

    private boolean isOwner(Account account, Authentication authentication) {
        return account.getCustomer().getUser().getUsername().equals(authentication.getName());
    }
}
