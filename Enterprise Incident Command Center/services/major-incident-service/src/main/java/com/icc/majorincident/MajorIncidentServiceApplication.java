package com.icc.majorincident;

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
@EnableFeignClients(basePackages = "com.icc.majorincident.client")
// The service scans only its own package, so the shared audit entity and repository are listed here.
@EntityScan({"com.icc.majorincident.entity", "com.icc.common.audit"})
@EnableJpaRepositories({"com.icc.majorincident.repository", "com.icc.common.audit"})
public class MajorIncidentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MajorIncidentServiceApplication.class, args);
    }
}
