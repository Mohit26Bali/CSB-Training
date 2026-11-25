package com.company.jwt.config;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AppConfig {

    /**
     * Strength 12 again: with JWT the password is checked ONCE, at login, not on
     * every request as with HTTP Basic, so the slower (safer) hash costs little.
     * The demo users' hashes are cost 10; BCrypt reads the cost from each hash,
     * so they still verify.
     */
    @Bean
    public PasswordEncoder passwordEncoder(@Value("${portal.bcrypt-strength:12}") int strength) {
        return new BCryptPasswordEncoder(strength);
    }

    /** "Now" for token issue and expiry, replaceable in tests. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
