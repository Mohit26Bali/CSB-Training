package com.icc.incident;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Needs the local MySQL (schema icc_incident) and DB_PASSWORD in the environment.
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "JWT_SECRET=incident-context-test-secret-incident-12345"
})
class IncidentServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
