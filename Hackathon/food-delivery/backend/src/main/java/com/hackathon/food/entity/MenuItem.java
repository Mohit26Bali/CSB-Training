package com.hackathon.food.entity;

import java.math.BigDecimal;
import java.util.Locale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A dish on the menu. Only active dishes can be ordered. */
@Entity
@Table(name = "menu_items")
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String name;

    @Column(length = 300)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MenuCategory category;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private boolean vegetarian;

    @Column(nullable = false)
    private boolean active = true;

    protected MenuItem() {
    }

    public MenuItem(String name, String description, MenuCategory category, BigDecimal price, boolean vegetarian, boolean active) {
        update(name, description, category, price, vegetarian, active);
    }

    public void update(String name, String description, MenuCategory category, BigDecimal price, boolean vegetarian, boolean active) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.price = price;
        this.vegetarian = vegetarian;
        this.active = active;
    }

    /** The picture file name without extension, for example "paneer-tikka". */
    public String imageKey() {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public MenuCategory getCategory() {
        return category;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public boolean isVegetarian() {
        return vegetarian;
    }

    public boolean isActive() {
        return active;
    }
}
