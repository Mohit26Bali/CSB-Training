package com.springlab.lab.micro.core;

import java.util.List;

/**
 * The simulated services of the Microservices Lab. Each one owns its tables: no other service may read or write them, and the
 * boundary test fails the build if a repository touches a table it does not own. The ids are also the node ids of the
 * architecture diagram ({@code <id>-db} is the node of the service's database).
 */
public enum ServiceName {
    GATEWAY("gateway", "API Gateway", "Single entry point: routes, authenticates and rate-limits requests. Owns no business data.", List.of()),
    USER("user", "User Service", "Customers and their credit limit.", List.of("micro_lab_users")),
    PRODUCT("product", "Product Service", "The product catalogue and prices.", List.of("micro_lab_products")),
    ORDER("order", "Order Service", "Orders, saga progress and the transactional outbox.",
            List.of("micro_lab_orders", "micro_lab_saga_steps", "micro_lab_outbox")),
    INVENTORY("inventory", "Inventory Service", "Stock levels and reservations.", List.of("micro_lab_stock", "micro_lab_reservations")),
    PAYMENT("payment", "Payment Service", "Payments and refunds.", List.of("micro_lab_payments")),
    NOTIFICATION("notification", "Notification Service", "Notifications and the ids of events it has already processed.",
            List.of("micro_lab_notifications", "micro_lab_processed_events")),
    REGISTRY("registry", "Service Registry", "Which instances of which service exist and whether they are healthy (simulated).",
            List.of("micro_lab_registry"));

    /** The external caller (the browser, a mobile app): not a service. */
    public static final String CLIENT_NODE = "client";

    private final String id;
    private final String title;
    private final String responsibility;
    private final List<String> ownedTables;

    ServiceName(String id, String title, String responsibility, List<String> ownedTables) {
        this.id = id;
        this.title = title;
        this.responsibility = responsibility;
        this.ownedTables = ownedTables;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String responsibility() {
        return responsibility;
    }

    public List<String> ownedTables() {
        return ownedTables;
    }

    /** The diagram node of this service's database, or null when it owns no tables. */
    public String databaseNode() {
        return ownedTables.isEmpty() ? null : id + "-db";
    }

    public static String nodeOf(ServiceName from) {
        return from == null ? CLIENT_NODE : from.id;
    }
}
