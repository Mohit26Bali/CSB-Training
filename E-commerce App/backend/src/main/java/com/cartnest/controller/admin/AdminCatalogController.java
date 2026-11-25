package com.cartnest.controller.admin;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cartnest.dto.BrandResponse;
import com.cartnest.dto.CategoryNode;
import com.cartnest.dto.admin.AdminCategoryResponse;
import com.cartnest.dto.admin.BrandRequest;
import com.cartnest.dto.admin.CategoryRequest;
import com.cartnest.service.AdminCatalogService;
import com.cartnest.service.CategoryService;

import jakarta.validation.Valid;

/** Categories and brands. */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCatalogController {

    private final AdminCatalogService catalogService;
    private final CategoryService categoryService;

    public AdminCatalogController(AdminCatalogService catalogService, CategoryService categoryService) {
        this.catalogService = catalogService;
        this.categoryService = categoryService;
    }

    @GetMapping("/categories")
    public List<CategoryNode> categories() {
        return categoryService.getTree();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminCategoryResponse createCategory(@Valid @RequestBody CategoryRequest request) {
        return catalogService.createCategory(request);
    }

    @PutMapping("/categories/{id}")
    public AdminCategoryResponse updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return catalogService.updateCategory(id, request);
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable Long id) {
        catalogService.deleteCategory(id);
    }

    @GetMapping("/brands")
    public List<BrandResponse> brands() {
        return catalogService.brands();
    }

    @PostMapping("/brands")
    @ResponseStatus(HttpStatus.CREATED)
    public BrandResponse createBrand(@Valid @RequestBody BrandRequest request) {
        return catalogService.createBrand(request);
    }

    @PutMapping("/brands/{id}")
    public BrandResponse renameBrand(@PathVariable Long id, @Valid @RequestBody BrandRequest request) {
        return catalogService.renameBrand(id, request);
    }

    @DeleteMapping("/brands/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBrand(@PathVariable Long id) {
        catalogService.deleteBrand(id);
    }
}
