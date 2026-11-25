package com.cartnest.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cartnest.dto.CancelOrderRequest;
import com.cartnest.dto.CheckoutPreview;
import com.cartnest.dto.CheckoutPreviewRequest;
import com.cartnest.dto.CouponResponse;
import com.cartnest.dto.OrderDetailResponse;
import com.cartnest.dto.OrderPage;
import com.cartnest.dto.PlaceOrderRequest;
import com.cartnest.security.SecurityUser;
import com.cartnest.service.CouponService;
import com.cartnest.service.OrderService;

import jakarta.validation.Valid;

/** Checkout and the customer's own orders. */
@RestController
@RequestMapping("/api")
public class OrderController {

    private final OrderService orderService;
    private final CouponService couponService;

    public OrderController(OrderService orderService, CouponService couponService) {
        this.orderService = orderService;
        this.couponService = couponService;
    }

    @GetMapping("/coupons")
    public List<CouponResponse> coupons(@AuthenticationPrincipal SecurityUser user) {
        return couponService.available(user.getId());
    }

    @PostMapping("/checkout/preview")
    public CheckoutPreview preview(@AuthenticationPrincipal SecurityUser user,
                                   @Valid @RequestBody CheckoutPreviewRequest request) {
        return orderService.preview(user.getId(), request);
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDetailResponse placeOrder(@AuthenticationPrincipal SecurityUser user,
                                          @Valid @RequestBody PlaceOrderRequest request) {
        return orderService.placeOrder(user, request);
    }

    @GetMapping("/orders")
    public OrderPage myOrders(@AuthenticationPrincipal SecurityUser user,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "10") int size) {
        return orderService.myOrders(user.getId(), page, size);
    }

    @GetMapping("/orders/{orderNumber}")
    public OrderDetailResponse order(@AuthenticationPrincipal SecurityUser user, @PathVariable String orderNumber) {
        return orderService.myOrder(user.getId(), orderNumber);
    }

    @PostMapping("/orders/{orderNumber}/cancel")
    public OrderDetailResponse cancel(@AuthenticationPrincipal SecurityUser user, @PathVariable String orderNumber,
                                      @Valid @RequestBody(required = false) CancelOrderRequest request) {
        return orderService.cancelMyOrder(user, orderNumber, request == null ? null : request.reason());
    }
}
