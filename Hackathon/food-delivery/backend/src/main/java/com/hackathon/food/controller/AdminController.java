package com.hackathon.food.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hackathon.food.dto.MenuBody;
import com.hackathon.food.dto.MenuItemResponse;
import com.hackathon.food.dto.OrderResponse;
import com.hackathon.food.dto.ReasonBody;
import com.hackathon.food.dto.SalesDashboard;
import com.hackathon.food.dto.StatusBody;
import com.hackathon.food.entity.MenuCategory;
import com.hackathon.food.entity.OrderStatus;
import com.hackathon.food.exception.FieldValidationException;
import com.hackathon.food.service.FoodService;

import jakarta.validation.Valid;

/** Restaurant endpoints: the order board, status changes, menu management and the sales dashboard. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final FoodService food;

    public AdminController(FoodService food) {
        this.food = food;
    }

    /**
     * The order board. status picks one status; scope=open keeps only orders that are not delivered or cancelled (the live board uses it).
     * page (from 0) and size (1 to 100) are optional; without them every matching order is returned, as before.
     */
    @GetMapping("/orders")
    public List<OrderResponse> orders(@RequestParam(required = false) OrderStatus status, @RequestParam(required = false) String scope,
                                      @RequestParam(required = false) String search, @RequestParam(required = false) Integer page,
                                      @RequestParam(required = false) Integer size) {
        if (scope != null && !scope.isBlank() && !scope.equalsIgnoreCase("open") && !scope.equalsIgnoreCase("all")) {
            throw new FieldValidationException("scope", "Scope must be open or all");
        }
        return food.adminOrders(status, "open".equalsIgnoreCase(scope), search, page, size);
    }

    @PutMapping("/orders/{id}/status")
    public OrderResponse status(@PathVariable Long id, @Valid @RequestBody StatusBody body, Authentication authentication) {
        return food.changeStatus(authentication.getName(), id, body.status());
    }

    @PutMapping("/orders/{id}/cancel")
    public OrderResponse cancel(@PathVariable Long id, @Valid @RequestBody ReasonBody body, Authentication authentication) {
        return food.cancelAsAdmin(authentication.getName(), id, body.reason());
    }

    @GetMapping("/menu")
    public List<MenuItemResponse> menu(@RequestParam(required = false) MenuCategory category, @RequestParam(required = false) Boolean vegetarian,
                                       @RequestParam(required = false) String search) {
        return food.adminMenu(category, vegetarian, search);
    }

    @PostMapping("/menu")
    public ResponseEntity<MenuItemResponse> create(@Valid @RequestBody MenuBody body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(food.createItem(body));
    }

    @PutMapping("/menu/{id}")
    public MenuItemResponse update(@PathVariable Long id, @Valid @RequestBody MenuBody body) {
        return food.updateItem(id, body);
    }

    @GetMapping("/dashboard")
    public SalesDashboard dashboard() {
        return food.dashboard();
    }
}
