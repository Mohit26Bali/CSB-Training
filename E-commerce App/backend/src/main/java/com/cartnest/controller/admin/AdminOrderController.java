package com.cartnest.controller.admin;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cartnest.dto.CancelOrderRequest;
import com.cartnest.dto.OrderDetailResponse;
import com.cartnest.dto.admin.AdminOrderRow;
import com.cartnest.dto.admin.OrderStatusRequest;
import com.cartnest.dto.admin.PageResponse;
import com.cartnest.entity.OrderStatus;
import com.cartnest.security.SecurityUser;
import com.cartnest.service.AdminOrderService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private final AdminOrderService orderService;

    public AdminOrderController(AdminOrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public PageResponse<AdminOrderRow> list(@RequestParam(required = false) OrderStatus status,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return orderService.list(status, q, page, size);
    }

    @GetMapping("/{orderNumber}")
    public OrderDetailResponse get(@PathVariable String orderNumber) {
        return orderService.get(orderNumber);
    }

    /** Moves the order one step: PLACED → CONFIRMED → SHIPPED → DELIVERED. */
    @PutMapping("/{orderNumber}/status")
    public OrderDetailResponse changeStatus(@AuthenticationPrincipal SecurityUser admin, @PathVariable String orderNumber,
                                            @Valid @RequestBody OrderStatusRequest request) {
        return orderService.moveTo(orderNumber, request.status(), request.note(), admin.getEmail());
    }

    @PostMapping("/{orderNumber}/cancel")
    public OrderDetailResponse cancel(@AuthenticationPrincipal SecurityUser admin, @PathVariable String orderNumber,
                                      @Valid @RequestBody(required = false) CancelOrderRequest request) {
        return orderService.cancel(orderNumber, request == null ? null : request.reason(), admin.getEmail());
    }
}
