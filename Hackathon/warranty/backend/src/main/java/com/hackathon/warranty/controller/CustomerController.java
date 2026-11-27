package com.hackathon.warranty.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hackathon.warranty.dto.ClaimRequest;
import com.hackathon.warranty.dto.ClaimResponse;
import com.hackathon.warranty.dto.ProductRequest;
import com.hackathon.warranty.dto.ProductResponse;
import com.hackathon.warranty.entity.ClaimStatus;
import com.hackathon.warranty.service.ClaimService;
import com.hackathon.warranty.service.ProductService;

import jakarta.validation.Valid;

/** Customer endpoints: a customer only ever registers, sees and claims on their own products. */
@RestController
@RequestMapping("/api")
public class CustomerController {

    private final ProductService productService;
    private final ClaimService claimService;

    public CustomerController(ProductService productService, ClaimService claimService) {
        this.productService = productService;
        this.claimService = claimService;
    }

    @GetMapping("/products/categories")
    public List<Map<String, String>> categories() {
        return productService.categories();
    }

    @PostMapping("/products")
    public ResponseEntity<ProductResponse> register(@Valid @RequestBody ProductRequest body, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.register(authentication.getName(), body));
    }

    @GetMapping("/products/my")
    public List<ProductResponse> myProducts(@RequestParam(required = false) String search, Authentication authentication) {
        return productService.myProducts(authentication.getName(), search);
    }

    @PostMapping("/claims")
    public ResponseEntity<ClaimResponse> file(@Valid @RequestBody ClaimRequest body, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(claimService.file(authentication.getName(), body));
    }

    @GetMapping("/claims/my")
    public List<ClaimResponse> myClaims(@RequestParam(required = false) ClaimStatus status, Authentication authentication) {
        return claimService.myClaims(authentication.getName(), status);
    }
}
