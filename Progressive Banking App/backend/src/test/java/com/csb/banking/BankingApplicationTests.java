package com.csb.banking;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: the whole Spring context (all layers wired together, JPA mappings valid against H2) starts up.
 * Uses the "test" profile from src/test/resources/application-test.properties.
 */
@SpringBootTest
@ActiveProfiles("test")
class BankingApplicationTests {

    @Test
    void contextLoads() {
    }
}
