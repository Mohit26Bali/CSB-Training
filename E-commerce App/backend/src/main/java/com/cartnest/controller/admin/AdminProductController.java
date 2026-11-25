package com.cartnest.controller.admin;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cartnest.dto.admin.ActiveRequest;
import com.cartnest.dto.admin.AdminProductDetail;
import com.cartnest.dto.admin.AdminProductRow;
import com.cartnest.dto.admin.AdminVariant;
import com.cartnest.dto.admin.PageResponse;
import com.cartnest.dto.admin.ProductUpsertRequest;
import com.cartnest.dto.admin.StockUpdateRequest;
import com.cartnest.service.AdminProductService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductController {

    private final AdminProductService productService;

    public AdminProductController(AdminProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/products")
    public PageResponse<AdminProductRow> list(@RequestParam(required = false) String q,
                                              @RequestParam(required = false) String category,
                                              @RequestParam(required = false) Boolean active,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return productService.list(q, category, active, page, size);
    }

    @GetMapping("/products/{id}")
    public AdminProductDetail get(@PathVariable Long id) {
        return productService.get(id);
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminProductDetail create(@Valid @RequestBody ProductUpsertRequest request) {
        return productService.create(request);
    }

    @PutMapping("/products/{id}")
    public AdminProductDetail update(@PathVariable Long id, @Valid @RequestBody ProductUpsertRequest request) {
        return productService.update(id, request);
    }

    @PatchMapping("/products/{id}/active")
    public AdminProductDetail setActive(@PathVariable Long id, @Valid @RequestBody ActiveRequest request) {
        return productService.setActive(id, request.active());
    }

    @PatchMapping("/variants/{id}/stock")
    public AdminVariant updateStock(@PathVariable Long id, @Valid @RequestBody StockUpdateRequest request) {
        return productService.updateStock(id, request.stock());
    }
}
