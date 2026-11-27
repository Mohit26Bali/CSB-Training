package com.hackathon.hotel.dto;

import java.math.BigDecimal;

public record TypeStats(String type, int rooms, int occupiedTonight, BigDecimal averagePrice) {
}
