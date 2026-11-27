package com.hackathon.food.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OrderRequest(
        @NotEmpty(message = "Add at least one dish to your order") @Size(max = 30, message = "An order can have at most 30 different dishes") List<@Valid OrderLineBody> items,
        @NotBlank(message = "Delivery address is required") @Size(min = 10, max = 200, message = "Address must be 10 to 200 characters") String address,
        @NotBlank(message = "Phone number is required") @Pattern(regexp = "^[0-9]{10}$", message = "Enter a 10 digit mobile number") String phone,
        @Size(max = 200, message = "Notes must be at most 200 characters") String notes) {
}
