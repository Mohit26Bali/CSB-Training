package com.cartnest.dto;

import com.cartnest.entity.AddressType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters") String fullName,
        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "[6-9][0-9]{9}", message = "Enter a 10-digit Indian mobile number") String phone,
        @NotBlank(message = "Address line 1 is required")
        @Size(max = 150, message = "Address line 1 must be at most 150 characters") String line1,
        @Size(max = 150, message = "Address line 2 must be at most 150 characters") String line2,
        @Size(max = 100, message = "Landmark must be at most 100 characters") String landmark,
        @NotBlank(message = "City is required")
        @Size(max = 60, message = "City must be at most 60 characters") String city,
        @NotBlank(message = "State is required") String state,
        @NotBlank(message = "Pincode is required")
        @Pattern(regexp = "[1-9][0-9]{5}", message = "Pincode must be 6 digits and cannot start with 0") String pincode,
        @NotNull(message = "Address type is required (HOME, WORK or OTHER)") AddressType type,
        Boolean makeDefault) {
}
