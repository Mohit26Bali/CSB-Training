package com.icc.rca;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Needs the local MySQL (schema icc_rca) and DB_PASSWORD in the environment.
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "JWT_SECRET=rca-context-test-secret-rca-context-12345"
})
class RcaServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
