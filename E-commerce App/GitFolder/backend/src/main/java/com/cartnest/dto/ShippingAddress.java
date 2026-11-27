package com.cartnest.dto;

public record ShippingAddress(String fullName, String phone, String line1, String line2, String landmark, String city,
        String state, String pincode) {
}
