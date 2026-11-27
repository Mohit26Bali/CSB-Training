package com.hackathon.hotel.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Create or edit a room (the admin's form). */
public record RoomBody(
        @NotBlank(message = "Room number is required") @Size(max = 10, message = "Room number must be at most 10 characters")
        @Pattern(regexp = "[A-Za-z0-9-]*", message = "Use letters, digits and dashes only") String number,
        @NotBlank(message = "Choose a room type") String type,
        @NotNull(message = "Floor is required") @Min(value = 0, message = "Floor cannot be negative") @Max(value = 60, message = "Floor must be at most 60") Integer floor,
        @NotNull(message = "Price per night is required") @DecimalMin(value = "100.00", message = "Price must be at least 100") @DecimalMax(value = "500000.00", message = "Price is too high") BigDecimal pricePerNight,
        @NotNull(message = "Capacity is required") @Min(value = 1, message = "At least 1 guest") @Max(value = 10, message = "At most 10 guests") Integer capacity,
        @Size(max = 500, message = "Description must be at most 500 characters") String description,
        @Size(max = 300, message = "Amenities must be at most 300 characters") String amenities,
        Boolean active) {
}
