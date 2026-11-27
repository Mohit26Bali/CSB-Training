package com.cartnest.dto;

import java.util.List;

public record HomeSection(String key, String title, String viewAll, List<ProductCard> products) {
}
