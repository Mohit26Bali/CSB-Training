package com.csb.cinescope.config;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.csb.cinescope.entity.Role;
import com.csb.cinescope.repository.UserRepository;

/**
 * Makes the very first administrator. A new system has none, and the admin screens cannot make one, so a person who runs the
 * deployment names an existing account in the setting {@code app.security.initial-admin} (environment variable
 * {@code INITIAL_ADMIN}); on start-up that account becomes an ADMIN. Nothing happens when the setting is empty, which is the
 * normal state, and nothing happens if the account is already an administrator, so it is safe to leave switched on.
 * If the named account does not exist yet, a warning says so (register it, then restart).
 *
 * <p>Connections: reads and changes the account through {@code repository/UserRepository}; the resulting ROLE_ADMIN is what
 * {@code config/SecurityConfig} and {@code service/AdminUserService} check.
 */
@Component
@ConditionalOnExpression("!'${app.security.initial-admin:}'.isBlank()")
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final TransactionTemplate transaction;
    private final String username;

    public AdminBootstrap(UserRepository users, TransactionTemplate transaction, @Value("${app.security.initial-admin}") String username) {
        this.users = users;
        this.transaction = transaction;
        this.username = username.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public void run(ApplicationArguments args) {
        transaction.executeWithoutResult(status -> users.findByUsername(username).ifPresentOrElse(user -> {
            if (user.getRole() != Role.ADMIN) {
                user.changeRole(Role.ADMIN);
                users.save(user);
                log.warn("Account '{}' was made an administrator (app.security.initial-admin).", username);
            }
        }, () -> log.warn("app.security.initial-admin names '{}', but no such account exists yet. Register it, then restart.", username)));
    }
}
