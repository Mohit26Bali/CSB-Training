package com.cartnest.entity;

import java.math.BigDecimal;

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
@Table(name = "product_variants")
public class ProductVariant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, unique = true, length = 40)
    private String sku;

    @Column(nullable = false, length = 40)
    private String label;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal mrp;

    @Column(nullable = false)
    private int stock;

    @Column(nullable = false)
    private int position;

    protected ProductVariant() {
    }

    public ProductVariant(Product product) {
        this.product = product;
    }

    public void update(String sku, String label, BigDecimal price, BigDecimal mrp, int stock, int position) {
        this.sku = sku;
        this.label = label;
        this.price = price;
        this.mrp = mrp;
        this.stock = stock;
        this.position = position;
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public String getSku() {
        return sku;
    }

    public String getLabel() {
        return label;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getMrp() {
        return mrp;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public int getPosition() {
        return position;
    }
}
