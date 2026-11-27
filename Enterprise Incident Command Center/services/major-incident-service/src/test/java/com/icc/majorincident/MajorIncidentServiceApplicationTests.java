package com.icc.majorincident;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Needs the local MySQL (schema icc_major_incident) and DB_PASSWORD in the environment.
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "JWT_SECRET=major-incident-context-test-secret-12345"
})
class MajorIncidentServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
