package com.cartnest.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything the checkout page shows before the order is placed. couponMessage explains why a coupon was not
 * applied; codAvailable is false above the cash-on-delivery limit.
 */
public record CheckoutPreview(List<CartItemResponse> items, int itemCount, BigDecimal totalMrp, BigDecimal productDiscount,
        BigDecimal itemTotal, String couponCode, BigDecimal couponDiscount, String couponMessage,
        BigDecimal deliveryFee, BigDecimal grandTotal, boolean codAvailable, AddressResponse address,
        boolean checkoutReady) {
}
