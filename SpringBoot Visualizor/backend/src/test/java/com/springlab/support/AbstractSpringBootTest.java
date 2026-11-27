package com.springlab.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.security.SecureRandom;
import java.util.HexFormat;

/** Full application context against the local MySQL test schema (application-test.yml). Not transactional. */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractSpringBootTest {

    /** A fresh random signing key per test run: no secret is stored in any file. */
    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        byte[] random = new byte[48];
        new SecureRandom().nextBytes(random);
        String secret = HexFormat.of().formatHex(random);
        registry.add("springlab.jwt.secret", () -> secret);
    }
}
