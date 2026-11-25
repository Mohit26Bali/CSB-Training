package com.csb.banking.config;

import java.math.BigDecimal;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.csb.banking.entity.Account;
import com.csb.banking.entity.AccountType;
import com.csb.banking.entity.Customer;
import com.csb.banking.entity.Role;
import com.csb.banking.entity.Transaction;
import com.csb.banking.entity.TransactionDirection;
import com.csb.banking.entity.TransactionStatus;
import com.csb.banking.entity.TransactionType;
import com.csb.banking.entity.User;
import com.csb.banking.repository.AccountRepository;
import com.csb.banking.repository.CustomerRepository;
import com.csb.banking.repository.TransactionRepository;
import com.csb.banking.repository.UserRepository;
import com.csb.banking.service.AccountNumberGenerator;

/**
 * Seeds demo data on startup so the API can be tried immediately.
 *
 * <p>Runs only when {@code app.seed.enabled=true} (set via SEED_DEMO_DATA, off by default; tests switch it off) and the {@code users} table is
 * empty, so restarting the application never duplicates data. It writes straight through the {@code repository/}
 * interfaces, bypassing {@code service/} (whose methods need a logged-in user), and uses
 * {@link AccountNumberGenerator} so seeded accounts look like real ones.
 *
 * <p>Demo logins: {@code admin / admin12345}, {@code ravi / customer123}, {@code priya / customer123}.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountNumberGenerator accountNumberGenerator;

    public DataInitializer(UserRepository userRepository, CustomerRepository customerRepository,
                           AccountRepository accountRepository, TransactionRepository transactionRepository,
                           PasswordEncoder passwordEncoder, AccountNumberGenerator accountNumberGenerator) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.passwordEncoder = passwordEncoder;
        this.accountNumberGenerator = accountNumberGenerator;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }
        userRepository.save(new User("admin", passwordEncoder.encode("admin12345"), "Bank Administrator", Role.ADMIN));

        Customer ravi = createCustomer("ravi", "Ravi Kumar", "ravi.kumar@example.com", "9876543210",
                "12 MG Road, Bengaluru");
        createAccount(ravi, AccountType.SAVINGS, "50000.00");

        Customer priya = createCustomer("priya", "Priya Sharma", "priya.sharma@example.com", "9123456780",
                "7 Park Street, Kolkata");
        createAccount(priya, AccountType.SAVINGS, "25000.00");
        createAccount(priya, AccountType.CURRENT, "100000.00");

        log.info("Demo data created: admin/admin12345, ravi/customer123, priya/customer123");
    }

    private Customer createCustomer(String username, String fullName, String email, String mobile, String address) {
        User user = userRepository.save(
                new User(username, passwordEncoder.encode("customer123"), fullName, Role.CUSTOMER));
        return customerRepository.save(new Customer(user, email, mobile, address));
    }

    private void createAccount(Customer customer, AccountType type, String openingBalance) {
        BigDecimal balance = new BigDecimal(openingBalance);
        Account account = accountRepository.save(
                new Account(accountNumberGenerator.next(), type, customer, balance));
        transactionRepository.save(new Transaction(account, UUID.randomUUID().toString(),
                TransactionType.DEPOSIT, TransactionDirection.CREDIT, balance, TransactionStatus.SUCCESS,
                "Opening deposit", null, balance));
    }
}
