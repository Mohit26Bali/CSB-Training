package com.hackathon.food.dto;

import com.hackathon.food.entity.OrderStatus;

import jakarta.validation.constraints.NotNull;

public record StatusBody(@NotNull(message = "Choose a status") OrderStatus status) {
}
