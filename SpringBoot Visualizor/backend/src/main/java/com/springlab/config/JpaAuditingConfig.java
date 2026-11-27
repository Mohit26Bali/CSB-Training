package com.springlab.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables {@code @CreatedDate} / {@code @LastModifiedDate} handling via AuditingEntityListener.
 * Kept in its own class (not on the main application class) so slice tests such as
 * {@code @WebMvcTest} do not require JPA metamodel setup.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
