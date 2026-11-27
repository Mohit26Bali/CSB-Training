package com.icc.incident;

import com.icc.common.audit.AuditConfig;
import com.icc.common.config.CommonConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
// The sync job (only created when icc.itsm.mode=servicenow) needs the scheduler.
@EnableScheduling
@Import({CommonConfig.class, AuditConfig.class})
@EnableFeignClients(basePackages = "com.icc.incident.client")
// The service scans only its own package, so the shared audit entity and repository are listed here.
@EntityScan({"com.icc.incident.entity", "com.icc.common.audit"})
@EnableJpaRepositories({"com.icc.incident.repository", "com.icc.common.audit"})
public class IncidentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IncidentServiceApplication.class, args);
    }
}
