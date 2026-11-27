package com.icc.assignment;

import com.icc.common.audit.AuditConfig;
import com.icc.common.config.CommonConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@Import({CommonConfig.class, AuditConfig.class})
// The service scans only its own package, so the shared audit entity and repository are listed here.
@EntityScan({"com.icc.assignment.entity", "com.icc.common.audit"})
@EnableJpaRepositories({"com.icc.assignment.repository", "com.icc.common.audit"})
public class AssignmentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AssignmentServiceApplication.class, args);
    }
}
