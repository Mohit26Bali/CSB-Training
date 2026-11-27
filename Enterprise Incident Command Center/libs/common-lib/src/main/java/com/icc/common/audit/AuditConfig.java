package com.icc.common.audit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Import this in a service that has a database. The service must also list "com.icc.common.audit"
 * in its @EntityScan and @EnableJpaRepositories, because it scans only its own packages.
 */
@Configuration
public class AuditConfig {

    @Bean
    public AuditService auditService(AuditEventRepository repository) {
        return new AuditService(repository);
    }
}
