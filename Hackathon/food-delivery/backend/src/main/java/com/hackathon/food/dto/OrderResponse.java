package com.hackathon.food.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import com.hackathon.food.entity.FoodOrder;
import com.hackathon.food.entity.OrderStatus;

/**
 * An order as the API shows it. Times are stored as local date-times in the restaurant's zone and sent with their offset
 * ("2026-11-28T00:29:37+05:30"), so a browser in any zone shows the right moment.
 */
public record OrderResponse(Long id, String number, String status, String nextStatus, List<OrderLineResponse> lines, int itemCount,
                            BigDecimal subtotal, BigDecimal tax, BigDecimal deliveryFee, BigDecimal total,
                            String customerName, String customerUsername, String address, String phone, String notes,
                            OffsetDateTime createdAt, OffsetDateTime acceptedAt, String acceptedBy, OffsetDateTime preparingAt, OffsetDateTime readyAt,
                            OffsetDateTime deliveredAt, OffsetDateTime cancelledAt, String cancelledBy, String cancelReason, boolean canCancel) {

    public static OrderResponse of(FoodOrder o, ZoneId zone) {
        OrderStatus next = o.getStatus().next();
        List<OrderLineResponse> lines = o.getItems().stream().map(OrderLineResponse::of).toList();
        return new OrderResponse(o.getId(), "FD-" + (1000 + o.getId()), o.getStatus().name(), next == null ? null : next.name(), lines,
                lines.stream().mapToInt(OrderLineResponse::quantity).sum(), o.getSubtotal(), o.getTax(), o.getDeliveryFee(), o.getTotal(),
                o.getCustomer().getFullname(), o.getCustomer().getUsername(), o.getAddress(), o.getPhone(), o.getNotes(),
                at(o.getCreatedAt(), zone), at(o.getAcceptedAt(), zone), o.getAcceptedBy(), at(o.getPreparingAt(), zone), at(o.getReadyAt(), zone),
                at(o.getDeliveredAt(), zone), at(o.getCancelledAt(), zone), o.getCancelledBy(), o.getCancelReason(), o.getStatus() == OrderStatus.PLACED);
    }

    private static OffsetDateTime at(LocalDateTime time, ZoneId zone) {
        return time == null ? null : time.atZone(zone).toOffsetDateTime();
    }
}
