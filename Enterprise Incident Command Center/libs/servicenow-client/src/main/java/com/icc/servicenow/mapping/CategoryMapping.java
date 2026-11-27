package com.icc.servicenow.mapping;

import java.util.Map;

/**
 * Our categories and subcategories in ServiceNow's words (D37: the translation lives here, in the adapter only).
 * ServiceNow stores a lower case value for each choice. Four categories are the same word; Access and Other have no
 * equivalent and go to Password Reset and Inquiry / Help.
 */
public final class CategoryMapping {

    private static final Map<String, String> CATEGORY = Map.of(
            "Hardware", "hardware",
            "Software", "software",
            "Network", "network",
            "Database", "database",
            "Access", "password_reset",
            "Other", "inquiry");

    /** Only the subcategories whose ServiceNow value is not simply the lower case label. */
    private static final Map<String, String> SUBCATEGORY_SPECIAL = Map.of(
            "Operating System", "os",
            "MS SQL Server", "sql server");

    private CategoryMapping() {
    }

    /** Our category (Hardware, Software, Network, Database, Access, Other) as ServiceNow's category value. */
    public static String category(String ours) {
        String value = CATEGORY.get(ours);
        if (value == null) {
            throw new IllegalArgumentException("No ServiceNow category for " + ours + ". Known: " + CATEGORY.keySet());
        }
        return value;
    }

    /** Our subcategory (for example "VPN" or "Operating System") as ServiceNow's value; null when there is none. */
    public static String subcategory(String ours) {
        if (ours == null || ours.isBlank()) {
            return null;
        }
        return SUBCATEGORY_SPECIAL.getOrDefault(ours, ours.toLowerCase());
    }
}
