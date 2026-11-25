package com.cartnest.dto;

/** One choice in a filter group, with the number of products it would show. */
public record FacetOption(String value, String label, long count) {
}
