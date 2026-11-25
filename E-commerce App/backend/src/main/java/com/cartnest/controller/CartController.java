package com.cartnest.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cartnest.dto.CartAddRequest;
import com.cartnest.dto.CartQuantityRequest;
import com.cartnest.dto.CartResponse;
import com.cartnest.security.SecurityUser;
import com.cartnest.service.CartService;

import jakarta.validation.Valid;

/** Every cart operation answers with the whole updated cart, so the page can redraw from one response. */
@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartResponse cart(@AuthenticationPrincipal SecurityUser user) {
        return cartService.getCart(user.getId());
    }

    @PostMapping("/items")
    public CartResponse add(@AuthenticationPrincipal SecurityUser user, @Valid @RequestBody CartAddRequest request) {
        return cartService.add(user.getId(), request);
    }

    @PutMapping("/items/{itemId}")
    public CartResponse updateQuantity(@AuthenticationPrincipal SecurityUser user, @PathVariable Long itemId,
                                       @Valid @RequestBody CartQuantityRequest request) {
        return cartService.updateQuantity(user.getId(), itemId, request.quantity());
    }

    @DeleteMapping("/items/{itemId}")
    public CartResponse remove(@AuthenticationPrincipal SecurityUser user, @PathVariable Long itemId) {
        return cartService.remove(user.getId(), itemId);
    }

    @PostMapping("/items/{itemId}/move-to-wishlist")
    public CartResponse moveToWishlist(@AuthenticationPrincipal SecurityUser user, @PathVariable Long itemId) {
        return cartService.moveToWishlist(user.getId(), itemId);
    }

    @DeleteMapping
    public CartResponse clear(@AuthenticationPrincipal SecurityUser user) {
        return cartService.clear(user.getId());
    }
}
