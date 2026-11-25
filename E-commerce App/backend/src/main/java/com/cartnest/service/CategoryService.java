package com.cartnest.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cartnest.dto.CategoryDetail;
import com.cartnest.dto.CategoryNode;
import com.cartnest.entity.Category;
import com.cartnest.exception.ResourceNotFoundException;
import com.cartnest.repository.CategoryRepository;
import com.cartnest.repository.IdCount;
import com.cartnest.repository.ProductRepository;

@Service
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryService(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    /** The full tree with product counts. */
    public List<CategoryNode> getTree() {
        CategoryTree tree = loadTree();
        return tree.roots().stream().map(tree::toNode).toList();
    }

    public CategoryDetail getBySlug(String slug) {
        CategoryTree tree = loadTree();
        Category category = findOrThrow(tree, slug);
        CategoryNode node = tree.toNode(category);
        return new CategoryDetail(category.getId(), category.getName(), category.getSlug(), category.getImageUrl(),
                node.productCount(), tree.breadcrumb(category.getId()), node.children());
    }

    public CategoryTree loadTree() {
        Map<Long, Long> counts = productRepository.countActiveByCategory().stream()
                .collect(Collectors.toMap(IdCount::id, IdCount::total));
        return new CategoryTree(categoryRepository.findAll(), counts);
    }

    public Category findOrThrow(CategoryTree tree, String slug) {
        return tree.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Category '" + slug + "' not found"));
    }
}
