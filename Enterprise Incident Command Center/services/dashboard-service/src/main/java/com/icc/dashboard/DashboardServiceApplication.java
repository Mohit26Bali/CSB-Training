package com.icc.dashboard;

import com.icc.common.config.CommonConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

// No database and no audit table: the Dashboard stores nothing (D5), it only reads from the other services.
@SpringBootApplication
@Import(CommonConfig.class)
@EnableFeignClients(basePackages = "com.icc.dashboard.client")
public class DashboardServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DashboardServiceApplication.class, args);
    }
}
