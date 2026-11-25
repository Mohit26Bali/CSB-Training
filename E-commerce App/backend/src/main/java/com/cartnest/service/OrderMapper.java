package com.cartnest.service;

import org.springframework.stereotype.Component;

import com.cartnest.dto.OrderDetailResponse;
import com.cartnest.dto.OrderEvent;
import com.cartnest.dto.OrderItemResponse;
import com.cartnest.dto.OrderSummaryResponse;
import com.cartnest.dto.ShippingAddress;
import com.cartnest.entity.CustomerOrder;
import com.cartnest.entity.OrderItem;

@Component
public class OrderMapper {

    public OrderSummaryResponse toSummary(CustomerOrder o) {
        return new OrderSummaryResponse(o.getOrderNumber(), o.getStatus(), o.getPlacedAt(), o.getGrandTotal(),
                o.getItems().stream().mapToInt(OrderItem::getQuantity).sum(),
                o.getItems().stream().map(OrderItem::getThumbnailUrl).distinct().limit(4).toList(),
                o.getPaymentMethod());
    }

    public OrderDetailResponse toDetail(CustomerOrder o) {
        return new OrderDetailResponse(o.getOrderNumber(), o.getStatus(), o.getPlacedAt(), o.getUser().getFullName(),
                o.getUser().getEmail(),
                o.getItems().stream().map(i -> new OrderItemResponse(i.getProductSlug(), i.getProductTitle(),
                        i.getVariantLabel(), i.getThumbnailUrl(), i.getUnitPrice(), i.getUnitMrp(), i.getQuantity(),
                        i.getLineTotal())).toList(),
                new ShippingAddress(o.getShipName(), o.getShipPhone(), o.getShipLine1(), o.getShipLine2(),
                        o.getShipLandmark(), o.getShipCity(), o.getShipState(), o.getShipPincode()),
                o.getPaymentMethod(), o.getPaymentStatus(), o.getPaymentReference(), o.getTotalMrp(),
                o.getTotalMrp().subtract(o.getItemTotal()), o.getItemTotal(), o.getCouponCode(), o.getCouponDiscount(),
                o.getDeliveryFee(), o.getGrandTotal(),
                o.getHistory().stream().map(h -> new OrderEvent(h.getStatus(), h.getNote(), h.getChangedAt())).toList(),
                o.isCancellable(), o.getCancelReason());
    }
}
