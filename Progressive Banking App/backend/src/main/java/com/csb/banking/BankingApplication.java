package com.csb.banking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the backend.
 *
 * <p>{@code @SpringBootApplication} component-scans this package and everything below it, which is how the
 * classes of every layer find each other without any manual wiring:
 * <pre>
 * HTTP -> controller -> service -> repository -> MySQL "bank"
 *            |            |            |
 *            +-- dto -----+            +-- entity
 *            security + config wrap the whole chain; exception turns failures into JSON
 * </pre>
 * Settings come from {@code src/main/resources/application.properties}.
 */
@SpringBootApplication
public class BankingApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankingApplication.class, args);
    }
}
