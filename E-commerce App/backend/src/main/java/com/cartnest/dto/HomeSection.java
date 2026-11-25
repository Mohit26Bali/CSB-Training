package com.cartnest.dto;

import java.util.List;

/** A product rail on the home page; viewAll is the listing-page query behind "View all". */
public record HomeSection(String key, String title, String viewAll, List<ProductCard> products) {
}
