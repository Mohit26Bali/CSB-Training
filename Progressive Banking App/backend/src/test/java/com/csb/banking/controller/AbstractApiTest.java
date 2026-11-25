package com.csb.banking.controller;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.csb.banking.entity.Account;
import com.csb.banking.entity.AccountType;
import com.csb.banking.entity.Customer;
import com.csb.banking.entity.Role;
import com.csb.banking.entity.User;
import com.csb.banking.repository.AccountRepository;
import com.csb.banking.repository.CustomerRepository;
import com.csb.banking.repository.IdempotencyRepository;
import com.csb.banking.repository.TransactionRepository;
import com.csb.banking.repository.UserRepository;
import com.csb.banking.security.JwtService;

/**
 * Shared fixture of the API tests ({@link AccountApiTest}, {@link TransactionApiTest}): an empty H2 database before
 * every test containing an admin, two customers (ravi, priya) and one account of ravi (balance 50 000.00), plus a
 * MockMvc wired through the real security filter chain and a ready-made bearer token per user. Users and tokens are
 * created directly through the repositories and {@code JwtService}, so tests do not depend on the login endpoint.
 */
@SpringBootTest
@ActiveProfiles("test")
abstract class AbstractApiTest {

    @Autowired WebApplicationContext context;
    @Autowired UserRepository userRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired AccountRepository accountRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired IdempotencyRepository idempotencyRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    MockMvc mvc;
    String adminToken;
    String raviToken;
    String priyaToken;
    Customer ravi;
    Customer priya;
    String raviAccountNumber;

    @BeforeEach
    void setUpFixture() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        idempotencyRepository.deleteAll();
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        customerRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(new User("admin", passwordEncoder.encode("admin12345"), "Admin", Role.ADMIN));
        ravi = customer("ravi", "Ravi Kumar", "ravi@example.com");
        priya = customer("priya", "Priya Sharma", "priya@example.com");
        raviAccountNumber = newAccount(ravi, "1000000001", "50000.00").getAccountNumber();

        adminToken = bearer("admin", "ADMIN");
        raviToken = bearer("ravi", "CUSTOMER");
        priyaToken = bearer("priya", "CUSTOMER");
    }

    Customer customer(String username, String fullName, String email) {
        User user = userRepository.save(
                new User(username, passwordEncoder.encode("customer123"), fullName, Role.CUSTOMER));
        return customerRepository.save(new Customer(user, email, "9876543210", "Some street"));
    }

    Account newAccount(Customer owner, String accountNumber, String balance) {
        return accountRepository.save(
                new Account(accountNumber, AccountType.SAVINGS, owner, new BigDecimal(balance)));
    }

    String bearer(String username, String role) {
        return "Bearer " + jwtService.generateToken(username, role);
    }

    BigDecimal balanceOf(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber).orElseThrow().getBalance();
    }
}
