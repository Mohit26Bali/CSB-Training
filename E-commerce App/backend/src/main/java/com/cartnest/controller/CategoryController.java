package com.cartnest.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cartnest.dto.CategoryDetail;
import com.cartnest.dto.CategoryNode;
import com.cartnest.service.CategoryService;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public List<CategoryNode> tree() {
        return categoryService.getTree();
    }

    @GetMapping("/{slug}")
    public CategoryDetail category(@PathVariable String slug) {
        return categoryService.getBySlug(slug);
    }
}
