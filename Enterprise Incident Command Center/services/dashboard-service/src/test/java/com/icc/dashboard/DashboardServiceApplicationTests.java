package com.icc.dashboard;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "JWT_SECRET=dashboard-context-test-secret-dashboard-12345"
})
class DashboardServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
