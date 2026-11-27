package com.hackathon.food.dto;

import java.math.BigDecimal;

import com.hackathon.food.entity.MenuItem;

public record MenuItemResponse(Long id, String name, String description, String category, BigDecimal price, boolean vegetarian, boolean active, String imageKey) {

    public static MenuItemResponse of(MenuItem m) {
        return new MenuItemResponse(m.getId(), m.getName(), m.getDescription(), m.getCategory().name(), m.getPrice(), m.isVegetarian(), m.isActive(), m.imageKey());
    }
}
