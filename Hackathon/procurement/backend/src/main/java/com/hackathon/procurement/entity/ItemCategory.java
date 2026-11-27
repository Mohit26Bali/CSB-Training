package com.hackathon.procurement.entity;

public enum ItemCategory {
    OFFICE_SUPPLIES("Office supplies"),
    HARDWARE("Hardware"),
    SOFTWARE("Software"),
    FURNITURE("Furniture"),
    SERVICES("Services"),
    OTHER("Other");

    private final String label;

    ItemCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
