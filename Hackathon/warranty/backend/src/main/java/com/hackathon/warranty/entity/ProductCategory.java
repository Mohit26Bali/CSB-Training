package com.hackathon.warranty.entity;

public enum ProductCategory {
    LAPTOP("Laptop"),
    MOBILE("Mobile phone"),
    TELEVISION("Television"),
    APPLIANCE("Home appliance"),
    AUDIO("Audio"),
    WEARABLE("Wearable"),
    OTHER("Other");

    private final String label;

    ProductCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
