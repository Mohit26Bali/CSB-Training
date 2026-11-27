package com.hackathon.food.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.hackathon.food.entity.FoodOrder;
import com.hackathon.food.entity.OrderStatus;

public record OrderResponse(Long id, String number, String status, String nextStatus, List<OrderLineResponse> lines, int itemCount,
                            BigDecimal subtotal, BigDecimal tax, BigDecimal deliveryFee, BigDecimal total,
                            String customerName, String customerUsername, String address, String phone, String notes,
                            LocalDateTime createdAt, LocalDateTime acceptedAt, String acceptedBy, LocalDateTime preparingAt, LocalDateTime readyAt,
                            LocalDateTime deliveredAt, LocalDateTime cancelledAt, String cancelledBy, String cancelReason, boolean canCancel) {

    public static OrderResponse of(FoodOrder o) {
        OrderStatus next = o.getStatus().next();
        List<OrderLineResponse> lines = o.getItems().stream().map(OrderLineResponse::of).toList();
        return new OrderResponse(o.getId(), "FD-" + (1000 + o.getId()), o.getStatus().name(), next == null ? null : next.name(), lines,
                lines.stream().mapToInt(OrderLineResponse::quantity).sum(), o.getSubtotal(), o.getTax(), o.getDeliveryFee(), o.getTotal(),
                o.getCustomer().getFullname(), o.getCustomer().getUsername(), o.getAddress(), o.getPhone(), o.getNotes(),
                o.getCreatedAt(), o.getAcceptedAt(), o.getAcceptedBy(), o.getPreparingAt(), o.getReadyAt(), o.getDeliveredAt(),
                o.getCancelledAt(), o.getCancelledBy(), o.getCancelReason(), o.getStatus() == OrderStatus.PLACED);
    }
}
