package com.cartnest.dto;

import com.cartnest.entity.AddressType;

public record AddressResponse(Long id, String fullName, String phone, String line1, String line2, String landmark,
        String city, String state, String pincode, AddressType type, boolean isDefault) {
}
