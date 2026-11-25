package com.cartnest.dto;

/** The delivery address as it was when the order was placed. */
public record ShippingAddress(String fullName, String phone, String line1, String line2, String landmark, String city,
        String state, String pincode) {
}
