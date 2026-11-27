package com.cartnest.dto;

import java.math.BigDecimal;
import java.util.List;

public record CheckoutPreview(List<CartItemResponse> items, int itemCount, BigDecimal totalMrp, BigDecimal productDiscount,
        BigDecimal itemTotal, String couponCode, BigDecimal couponDiscount, String couponMessage,
        BigDecimal deliveryFee, BigDecimal grandTotal, boolean codAvailable, AddressResponse address,
        boolean checkoutReady) {
}
