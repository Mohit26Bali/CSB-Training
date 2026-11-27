package com.hackathon.asset.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A piece of equipment. The asset tag and the serial number are both unique. */
@Entity
@Table(name = "assets")
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String assetTag;

    @Column(nullable = false, unique = true, length = 30)
    private String serialNumber;

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private AssetCategory category;

    @Column(length = 40)
    private String brand;

    @Column(length = 40)
    private String model;

    private LocalDate purchaseDate;

    @Column(precision = 12, scale = 2)
    private BigDecimal purchasePrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_state", nullable = false, length = 6)
    private AssetCondition condition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AssetStatus status = AssetStatus.AVAILABLE;

    @Column(length = 300)
    private String notes;

    protected Asset() {
    }

    public Asset(String assetTag, String serialNumber, String name, AssetCategory category, String brand, String model, LocalDate purchaseDate,
                 BigDecimal purchasePrice, AssetCondition condition, String notes) {
        update(assetTag, serialNumber, name, category, brand, model, purchaseDate, purchasePrice, condition, notes);
    }

    public void update(String assetTag, String serialNumber, String name, AssetCategory category, String brand, String model, LocalDate purchaseDate,
                       BigDecimal purchasePrice, AssetCondition condition, String notes) {
        this.assetTag = assetTag;
        this.serialNumber = serialNumber;
        this.name = name;
        this.category = category;
        this.brand = brand;
        this.model = model;
        this.purchaseDate = purchaseDate;
        this.purchasePrice = purchasePrice;
        this.condition = condition;
        this.notes = notes;
    }

    public void setStatus(AssetStatus status) {
        this.status = status;
    }

    public void setCondition(AssetCondition condition) {
        this.condition = condition;
    }

    public Long getId() {
        return id;
    }

    public String getAssetTag() {
        return assetTag;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public String getName() {
        return name;
    }

    public AssetCategory getCategory() {
        return category;
    }

    public String getBrand() {
        return brand;
    }

    public String getModel() {
        return model;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public BigDecimal getPurchasePrice() {
        return purchasePrice;
    }

    public AssetCondition getCondition() {
        return condition;
    }

    public AssetStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }
}
