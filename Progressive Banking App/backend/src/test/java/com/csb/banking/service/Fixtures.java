package com.csb.banking.service;

import java.math.BigDecimal;

import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.csb.banking.entity.Account;
import com.csb.banking.entity.AccountStatus;
import com.csb.banking.entity.AccountType;
import com.csb.banking.entity.Customer;
import com.csb.banking.entity.Role;
import com.csb.banking.entity.User;

/**
 * Tiny object factory shared by the service unit tests in this package, so each test reads as the scenario it checks
 * instead of as setup code. Nothing here touches Spring or a database: the entities are plain objects and the
 * repositories are Mockito mocks in the tests themselves.
 */
final class Fixtures {

    private Fixtures() {
    }

    static Customer customer(String username) {
        User user = new User(username, "hash", username + " full name", Role.CUSTOMER);
        return new Customer(user, username + "@example.com", "9876543210", "Some street");
    }

    static Account account(String number, Customer owner, String balance) {
        return new Account(number, AccountType.SAVINGS, owner, new BigDecimal(balance));
    }

    static Account account(String number, Customer owner, String balance, AccountStatus status) {
        Account account = account(number, owner, balance);
        account.setStatus(status);
        return account;
    }

    static Authentication customerAuth(String username) {
        return new TestingAuthenticationToken(username, null, "ROLE_CUSTOMER");
    }

    static Authentication adminAuth() {
        return new TestingAuthenticationToken("admin", null, "ROLE_ADMIN");
    }

    static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
