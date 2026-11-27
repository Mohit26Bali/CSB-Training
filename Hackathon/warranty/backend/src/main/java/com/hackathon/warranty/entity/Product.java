package com.hackathon.warranty.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "products", uniqueConstraints = @UniqueConstraint(columnNames = "serial_number"))
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 80)
    private String brand;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private ProductCategory category;

    /** Stored in upper case, so "ab-1" and "AB-1" are the same product. */
    @Column(name = "serial_number", nullable = false, length = 40)
    private String serialNumber;

    @Column(nullable = false)
    private LocalDate purchaseDate;

    @Column(nullable = false)
    private int warrantyMonths;

    protected Product() {
    }

    public Product(User owner, String name, String brand, ProductCategory category, String serialNumber,
                   LocalDate purchaseDate, int warrantyMonths) {
        this.owner = owner;
        this.name = name;
        this.brand = brand;
        this.category = category;
        this.serialNumber = serialNumber;
        this.purchaseDate = purchaseDate;
        this.warrantyMonths = warrantyMonths;
    }

    /** The last day the warranty covers: the purchase date plus the warranty period. */
    public LocalDate getWarrantyEnd() {
        return purchaseDate.plusMonths(warrantyMonths);
    }

    public boolean isWarrantyActive(LocalDate today) {
        return !getWarrantyEnd().isBefore(today);
    }

    public Long getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getBrand() {
        return brand;
    }

    public ProductCategory getCategory() {
        return category;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public int getWarrantyMonths() {
        return warrantyMonths;
    }
}
