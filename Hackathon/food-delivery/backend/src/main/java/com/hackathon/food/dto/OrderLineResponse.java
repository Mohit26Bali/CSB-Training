package com.hackathon.food.dto;

import java.math.BigDecimal;

import com.hackathon.food.entity.OrderItem;

public record OrderLineResponse(Long menuItemId, String name, String imageKey, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {

    public static OrderLineResponse of(OrderItem i) {
        return new OrderLineResponse(i.getMenuItem().getId(), i.getItemName(), i.getMenuItem().imageKey(), i.getUnitPrice(), i.getQuantity(), i.lineTotal());
    }
}
