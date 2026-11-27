package com.icc.incident.service;

import com.icc.common.error.BadRequestException;

import java.util.List;
import java.util.Map;

/**
 * The fixed lists of categories and subcategories (DATA-MODEL). The categories must match the categories of the
 * Assignment rules. The subcategories are ServiceNow's, and each belongs to one category.
 */
public final class IncidentCategories {

    public static final List<String> ALL = List.of("Hardware", "Software", "Network", "Database", "Access", "Other");

    /** Access and Other have no subcategories. */
    public static final Map<String, List<String>> SUBCATEGORIES = Map.of(
            "Hardware", List.of("Memory", "Mouse", "Disk", "Monitor", "CPU", "Keyboard"),
            "Software", List.of("Email", "Operating System"),
            "Network", List.of("IP Address", "VPN", "DNS", "Wireless", "DHCP"),
            "Database", List.of("MS SQL Server", "DB2", "Oracle"));

    private IncidentCategories() {
    }

    /** Returns the category in its official spelling, or fails with a 400. */
    public static String require(String category) {
        String wanted = category == null ? "" : category.trim();
        return ALL.stream()
                .filter(known -> known.equalsIgnoreCase(wanted))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Unknown category " + category + ". Allowed: " + ALL));
    }

    /**
     * Returns the subcategory in its official spelling, null when none was given, or fails with a 400 when it
     * does not belong to the category (the same dependency as in ServiceNow).
     */
    public static String requireSubcategory(String category, String subcategory) {
        if (subcategory == null || subcategory.isBlank()) {
            return null;
        }
        String wanted = subcategory.trim();
        List<String> allowed = SUBCATEGORIES.getOrDefault(category, List.of());
        return allowed.stream()
                .filter(known -> known.equalsIgnoreCase(wanted))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Unknown subcategory " + subcategory + " for category "
                        + category + ". Allowed: " + allowed));
    }
}
