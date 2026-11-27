package com.cartnest.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.cartnest.dto.CategoryNode;
import com.cartnest.dto.CategoryRef;
import com.cartnest.entity.Category;

public class CategoryTree {
    private final Map<Long, Category> byId;
    private final Map<Long, List<Category>> childrenByParentId;
    private final Map<Long, Long> directProductCounts;

    public CategoryTree(List<Category> categories, Map<Long, Long> directProductCounts) {
        this.byId = categories.stream().collect(Collectors.toMap(Category::getId, Function.identity()));
        this.childrenByParentId = categories.stream()
                .filter(c -> c.getParent() != null)
                .sorted(Comparator.comparingInt(Category::getSortOrder))
                .collect(Collectors.groupingBy(c -> c.getParent().getId()));
        this.directProductCounts = directProductCounts;
    }

    public Optional<Category> findBySlug(String slug) {
        return byId.values().stream().filter(c -> c.getSlug().equalsIgnoreCase(slug)).findFirst();
    }

    public List<Category> roots() {
        return byId.values().stream()
                .filter(c -> c.getParent() == null)
                .sorted(Comparator.comparingInt(Category::getSortOrder))
                .toList();
    }

    public List<Category> all() {
        return List.copyOf(byId.values());
    }

    public List<Category> children(Category category) {
        return childrenByParentId.getOrDefault(category.getId(), Collections.emptyList());
    }

    public Set<Long> subtreeIds(Category category) {
        Set<Long> ids = new HashSet<>();
        ids.add(category.getId());
        children(category).forEach(child -> ids.addAll(subtreeIds(child)));
        return ids;
    }

    public long productCount(Category category) {
        return subtreeIds(category).stream().mapToLong(id -> directProductCounts.getOrDefault(id, 0L)).sum();
    }

    public List<CategoryRef> breadcrumb(Long categoryId) {
        List<CategoryRef> path = new ArrayList<>();
        for (Category c = byId.get(categoryId); c != null; c = c.getParent() == null ? null : byId.get(c.getParent().getId())) {
            path.add(0, new CategoryRef(c.getName(), c.getSlug()));
        }
        return path;
    }

    public Category topLevelOf(Long categoryId) {
        Category c = byId.get(categoryId);
        while (c.getParent() != null) {
            c = byId.get(c.getParent().getId());
        }
        return c;
    }

    public CategoryNode toNode(Category category) {
        return new CategoryNode(category.getId(), category.getName(), category.getSlug(), category.getImageUrl(),
                productCount(category), children(category).stream().map(this::toNode).toList());
    }
}
