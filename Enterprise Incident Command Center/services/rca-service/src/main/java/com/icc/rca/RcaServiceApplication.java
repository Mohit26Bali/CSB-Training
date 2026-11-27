package com.icc.rca;

import com.icc.common.audit.AuditConfig;
import com.icc.common.config.CommonConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@Import({CommonConfig.class, AuditConfig.class})
@EnableFeignClients(basePackages = "com.icc.rca.client")
// The service scans only its own package, so the shared audit entity and repository are listed here.
@EntityScan({"com.icc.rca.entity", "com.icc.common.audit"})
@EnableJpaRepositories({"com.icc.rca.repository", "com.icc.common.audit"})
public class RcaServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RcaServiceApplication.class, args);
    }
}
