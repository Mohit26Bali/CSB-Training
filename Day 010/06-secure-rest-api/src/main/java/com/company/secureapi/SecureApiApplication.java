package com.company.secureapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Production-style employee service: JWT + roles + ownership, DTOs, validation, JSON security errors. */
@SpringBootApplication
public class SecureApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecureApiApplication.class, args);
    }
}
