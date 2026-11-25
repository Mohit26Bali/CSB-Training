package com.cartnest.dto;

import java.util.List;

/** checkoutReady is false while any line has a problem. */
public record CartResponse(List<CartItemResponse> items, CartSummary summary, boolean checkoutReady) {
}
