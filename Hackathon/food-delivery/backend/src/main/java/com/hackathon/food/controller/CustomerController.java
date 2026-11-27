package com.hackathon.food.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hackathon.food.dto.MenuItemResponse;
import com.hackathon.food.dto.OrderRequest;
import com.hackathon.food.dto.OrderResponse;
import com.hackathon.food.dto.ReasonBody;
import com.hackathon.food.entity.MenuCategory;
import com.hackathon.food.service.FoodService;

import jakarta.validation.Valid;

/** Customer endpoints (the menu is open to any signed-in user): browse, order, follow and cancel. */
@RestController
@RequestMapping("/api")
public class CustomerController {

    private final FoodService food;

    public CustomerController(FoodService food) {
        this.food = food;
    }

    @GetMapping("/menu")
    public List<MenuItemResponse> menu(@RequestParam(required = false) MenuCategory category, @RequestParam(required = false) Boolean vegetarian,
                                       @RequestParam(required = false) String search) {
        return food.menu(category, vegetarian, search);
    }

    @GetMapping("/menu/categories")
    public List<String> categories() {
        return food.categories();
    }

    /**
     * Place an order. Send an Idempotency-Key header (a UUID made for this checkout) to make a retry safe: the first call answers 201 Created,
     * a retry with the same key answers 200 OK with the same order instead of making a second one.
     */
    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> place(@Valid @RequestBody OrderRequest body, @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                               Authentication authentication) {
        FoodService.Placement placed = food.place(authentication.getName(), body, idempotencyKey);
        return ResponseEntity.status(placed.created() ? HttpStatus.CREATED : HttpStatus.OK).body(placed.order());
    }

    /** scope: active, past or all. page (from 0) and size (1 to 100) are optional; without them every matching order is returned. */
    @GetMapping("/orders/my")
    public List<OrderResponse> mine(@RequestParam(required = false) String scope, @RequestParam(required = false) Integer page,
                                    @RequestParam(required = false) Integer size, Authentication authentication) {
        return food.myOrders(authentication.getName(), scope, page, size);
    }

    @GetMapping("/orders/{id}")
    public OrderResponse one(@PathVariable Long id, Authentication authentication) {
        return food.myOrder(authentication.getName(), id);
    }

    /** Cancel while the order is still PLACED. The reason travels in the body, so it does not end up in access logs. */
    @PostMapping("/orders/{id}/cancel")
    public OrderResponse cancelWithReason(@PathVariable Long id, @Valid @RequestBody(required = false) ReasonBody body, Authentication authentication) {
        return food.cancelOwn(authentication.getName(), id, body == null ? null : body.reason());
    }

    /** The original form, kept so existing clients keep working: the reason is a query parameter. Prefer POST /orders/{id}/cancel. */
    @DeleteMapping("/orders/{id}")
    public OrderResponse cancel(@PathVariable Long id, @RequestParam(required = false) String reason, Authentication authentication) {
        return food.cancelOwn(authentication.getName(), id, reason);
    }
}
