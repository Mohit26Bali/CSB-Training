package com.icc.assignment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Needs the local MySQL (schema icc_assignment) and DB_PASSWORD in the environment.
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "JWT_SECRET=assignment-context-test-secret-assignment-12345"
})
class AssignmentServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
