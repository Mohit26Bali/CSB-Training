package com.hackathon.food;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;

import com.hackathon.food.config.RequiredSettings;

@SpringBootApplication
public class FoodApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(FoodApplication.class);
        // Fail at once, naming the missing settings, before any connection to the database is tried.
        app.addListeners((ApplicationListener<ApplicationEnvironmentPreparedEvent>) event -> RequiredSettings.check(event.getEnvironment()));
        app.run(args);
    }
}
