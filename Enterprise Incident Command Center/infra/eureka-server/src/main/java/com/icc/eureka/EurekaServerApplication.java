package com.icc.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/*
@SpringBootApplication turns on component scanning in this package and sub-packages, auto-configuration, and configuration classes. It is the entry point.
 */

@SpringBootApplication

/*
@EnableEurekaServer switches this app into registry mode.
 */

@EnableEurekaServer
public class EurekaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
