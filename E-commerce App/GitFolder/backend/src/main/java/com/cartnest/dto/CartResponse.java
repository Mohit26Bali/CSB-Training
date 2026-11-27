package com.cartnest.dto;

import java.util.List;

public record CartResponse(List<CartItemResponse> items, CartSummary summary, boolean checkoutReady) {
}
