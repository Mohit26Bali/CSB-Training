package com.springlab.lab.meta;

import com.springlab.config.JwtProperties;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.springframework.boot.SpringBootVersion;
import org.springframework.core.SpringVersion;
import org.springframework.core.env.Environment;
import org.springframework.security.core.SpringSecurityCoreVersion;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class FrameworkMetaService {

    private final Environment environment;
    private final EntityManagerFactory entityManagerFactory;
    private final JwtProperties jwtProperties;

    public FrameworkMetaService(Environment environment, EntityManagerFactory entityManagerFactory,
                                JwtProperties jwtProperties) {
        this.environment = environment;
        this.entityManagerFactory = entityManagerFactory;
        this.jwtProperties = jwtProperties;
    }

    public FrameworkMeta meta() {
        Map<String, String> versions = new LinkedHashMap<>();
        versions.put("java", Runtime.version().toString());
        versions.put("springBoot", SpringBootVersion.getVersion());
        versions.put("springFramework", SpringVersion.getVersion());
        versions.put("springSecurity", SpringSecurityCoreVersion.getVersion());
        versions.put("hibernate", org.hibernate.Version.getVersionString());

        Map<String, String> settings = new LinkedHashMap<>();
        // Spring Boot's default for open-in-view is true when the property is absent.
        settings.put("spring.jpa.open-in-view",
                String.valueOf(environment.getProperty("spring.jpa.open-in-view", Boolean.class, true)));
        settings.put("spring.jpa.hibernate.ddl-auto",
                environment.getProperty("spring.jpa.hibernate.ddl-auto", "(not set)"));
        settings.put("hibernate.dialect", entityManagerFactory.unwrap(SessionFactoryImplementor.class)
                .getJdbcServices().getDialect().getClass().getSimpleName());
        settings.put("jwt.accessTokenLifetime", jwtProperties.accessTokenTtl().toString());
        settings.put("jwt.signingAlgorithm", "HS256");
        return new FrameworkMeta(Map.copyOf(versions), Map.copyOf(settings));
    }
}
