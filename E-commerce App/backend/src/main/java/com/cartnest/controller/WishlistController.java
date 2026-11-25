package com.cartnest.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cartnest.dto.WishlistAddRequest;
import com.cartnest.dto.WishlistItemResponse;
import com.cartnest.security.SecurityUser;
import com.cartnest.service.WishlistService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/wishlist")
public class WishlistController {

    private final WishlistService wishlistService;

    public WishlistController(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    @GetMapping
    public List<WishlistItemResponse> list(@AuthenticationPrincipal SecurityUser user) {
        return wishlistService.list(user.getId());
    }

    /** 201 when the product was added, 200 when it was already there. Either way the body is the wishlist. */
    @PostMapping
    public ResponseEntity<List<WishlistItemResponse>> add(@AuthenticationPrincipal SecurityUser user,
                                                          @Valid @RequestBody WishlistAddRequest request) {
        boolean added = wishlistService.add(user.getId(), request.productId());
        return ResponseEntity.status(added ? HttpStatus.CREATED : HttpStatus.OK).body(wishlistService.list(user.getId()));
    }

    @DeleteMapping("/{productId}")
    public List<WishlistItemResponse> remove(@AuthenticationPrincipal SecurityUser user, @PathVariable Long productId) {
        wishlistService.remove(user.getId(), productId);
        return wishlistService.list(user.getId());
    }
}
