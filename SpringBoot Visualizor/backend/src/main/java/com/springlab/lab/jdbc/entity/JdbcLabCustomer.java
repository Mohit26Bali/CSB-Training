package com.springlab.lab.jdbc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The SAME table the JDBC and Spring JDBC experiments use (jdbc_lab_customers), mapped as a JPA entity so the three access paths
 * can be compared on identical data. Never serialized: only DTOs leave the server.
 */
@Entity
@Table(name = "jdbc_lab_customers")
public class JdbcLabCustomer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_key", nullable = false, length = 32, updatable = false)
    private String runKey;

    @Column(nullable = false, length = 80)
    private String name;

    protected JdbcLabCustomer() {
    }

    public JdbcLabCustomer(String runKey, String name) {
        this.runKey = runKey;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
