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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hackathon.food.dto.MenuItemResponse;
import com.hackathon.food.dto.OrderRequest;
import com.hackathon.food.dto.OrderResponse;
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

    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> place(@Valid @RequestBody OrderRequest body, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(food.place(authentication.getName(), body));
    }

    @GetMapping("/orders/my")
    public List<OrderResponse> mine(@RequestParam(required = false) String scope, Authentication authentication) {
        return food.myOrders(authentication.getName(), scope);
    }

    @GetMapping("/orders/{id}")
    public OrderResponse one(@PathVariable Long id, Authentication authentication) {
        return food.myOrder(authentication.getName(), id);
    }

    @DeleteMapping("/orders/{id}")
    public OrderResponse cancel(@PathVariable Long id, @RequestParam(required = false) String reason, Authentication authentication) {
        return food.cancelOwn(authentication.getName(), id, reason);
    }
}
