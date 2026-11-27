package com.cartnest.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cartnest.dto.ProductDetail;
import com.cartnest.dto.ProductPage;
import com.cartnest.dto.ProductSearchRequest;
import com.cartnest.dto.MyReviewResponse;
import com.cartnest.dto.ReviewPage;
import com.cartnest.dto.ReviewRequest;
import com.cartnest.dto.ReviewResponse;
import com.cartnest.security.SecurityUser;
import com.cartnest.service.CatalogSearchService;
import com.cartnest.service.ProductService;
import com.cartnest.service.ReviewService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final CatalogSearchService searchService;
    private final ProductService productService;
    private final ReviewService reviewService;

    public ProductController(CatalogSearchService searchService, ProductService productService,
                             ReviewService reviewService) {
        this.searchService = searchService;
        this.productService = productService;
        this.reviewService = reviewService;
    }

    @GetMapping
    public ProductPage search(@Valid ProductSearchRequest request) {
        return searchService.search(request);
    }

    @GetMapping("/{slug}")
    public ProductDetail product(@PathVariable String slug) {
        return productService.getDetail(slug);
    }

    @GetMapping("/{slug}/reviews")
    public ReviewPage reviews(@PathVariable String slug,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "10") int size,
                              @RequestParam(defaultValue = "newest") String sort) {
        return productService.getReviews(slug, page, size, sort);
    }

    @GetMapping("/{slug}/reviews/mine")
    public MyReviewResponse myReview(@AuthenticationPrincipal SecurityUser user, @PathVariable String slug) {
        return reviewService.mine(user.getId(), slug);
    }

    @PostMapping("/{slug}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse writeReview(@AuthenticationPrincipal SecurityUser user, @PathVariable String slug,
                                      @Valid @RequestBody ReviewRequest request) {
        return reviewService.write(user.getId(), slug, request);
    }
}
