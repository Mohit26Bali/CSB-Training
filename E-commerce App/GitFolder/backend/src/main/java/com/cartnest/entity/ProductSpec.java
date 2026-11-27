package com.cartnest.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_specs")
public class ProductSpec {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false, length = 200)
    private String value;

    @Column(nullable = false)
    private int position;

    protected ProductSpec() {
    }

    public ProductSpec(Product product, String name, String value, int position) {
        this.product = product;
        this.name = name;
        this.value = value;
        this.position = position;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public int getPosition() {
        return position;
    }
}
