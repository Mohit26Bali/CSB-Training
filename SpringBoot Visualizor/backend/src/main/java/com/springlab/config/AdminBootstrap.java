package com.springlab.config;

import com.springlab.entity.Role;
import com.springlab.entity.User;
import com.springlab.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Creates the first administrator at startup from environment-based configuration. There are no default credentials:
 * if the variables are not set, nothing is created.
 *
 * <p>Rules: all three values or none; skipped when an active administrator already exists (so restarting never resets
 * a password); refuses to continue if the username/email already belongs to another account (it never silently
 * promotes an existing user). The password is never logged.
 */
@Component
@EnableConfigurationProperties(BootstrapAdminProperties.class)
public class AdminBootstrap implements ApplicationRunner {

    static final int MIN_PASSWORD_LENGTH = 12;
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_.-]{3,50}");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final BootstrapAdminProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrap(BootstrapAdminProperties properties, UserRepository userRepository,
                          PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    void seed() {
        boolean hasUsername = hasText(properties.username());
        boolean hasEmail = hasText(properties.email());
        boolean hasPassword = hasText(properties.password());

        if (!hasUsername && !hasEmail && !hasPassword) {
            log.info("No bootstrap administrator configured (ADMIN_USERNAME / ADMIN_EMAIL / ADMIN_PASSWORD not set)");
            return;
        }
        if (!(hasUsername && hasEmail && hasPassword)) {
            throw new IllegalStateException(
                    "ADMIN_USERNAME, ADMIN_EMAIL and ADMIN_PASSWORD must all be set to seed the initial administrator");
        }
        if (userRepository.countByRoleAndEnabled(Role.ADMIN, true) > 0) {
            log.info("An active administrator already exists; bootstrap administrator not created");
            return;
        }

        String username = properties.username().trim();
        String email = properties.email().trim().toLowerCase(Locale.ROOT);
        validate(username, email, properties.password());

        if (userRepository.existsByUsername(username) || userRepository.existsByEmail(email)) {
            throw new IllegalStateException("Bootstrap administrator username or email is already used by another "
                    + "account; refusing to modify an existing account automatically");
        }
        userRepository.save(new User(username, email, passwordEncoder.encode(properties.password()), Role.ADMIN));
        log.info("Created bootstrap administrator '{}'", username);
    }

    private static void validate(String username, String email, String password) {
        if (!USERNAME.matcher(username).matches()) {
            throw new IllegalStateException("ADMIN_USERNAME must be 3-50 characters: letters, digits, '.', '_' or '-'");
        }
        if (!EMAIL.matcher(email).matches() || email.length() > 120) {
            throw new IllegalStateException("ADMIN_EMAIL is not a valid email address");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("ADMIN_PASSWORD must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("ADMIN_PASSWORD must be at most 72 bytes (BCrypt limit)");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
