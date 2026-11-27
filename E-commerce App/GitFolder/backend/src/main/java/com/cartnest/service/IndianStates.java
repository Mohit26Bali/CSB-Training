package com.cartnest.service;

import java.util.List;
import java.util.Optional;

public final class IndianStates {
    public static final List<String> ALL = List.of(
            "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh", "Goa", "Gujarat", "Haryana",
            "Himachal Pradesh", "Jharkhand", "Karnataka", "Kerala", "Madhya Pradesh", "Maharashtra", "Manipur",
            "Meghalaya", "Mizoram", "Nagaland", "Odisha", "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu", "Telangana",
            "Tripura", "Uttar Pradesh", "Uttarakhand", "West Bengal",
            "Andaman and Nicobar Islands", "Chandigarh", "Dadra and Nagar Haveli and Daman and Diu", "Delhi",
            "Jammu and Kashmir", "Ladakh", "Lakshadweep", "Puducherry");

    private IndianStates() {
    }

    public static Optional<String> canonical(String name) {
        return ALL.stream().filter(s -> s.equalsIgnoreCase(name.trim())).findFirst();
    }
}
