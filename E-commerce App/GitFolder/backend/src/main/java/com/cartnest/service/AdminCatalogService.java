package com.cartnest.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cartnest.dto.BrandResponse;
import com.cartnest.dto.admin.AdminCategoryResponse;
import com.cartnest.dto.admin.BrandRequest;
import com.cartnest.dto.admin.CategoryRequest;
import com.cartnest.entity.Brand;
import com.cartnest.entity.Category;
import com.cartnest.exception.BadRequestException;
import com.cartnest.exception.ConflictException;
import com.cartnest.exception.ResourceNotFoundException;
import com.cartnest.repository.BrandRepository;
import com.cartnest.repository.CategoryRepository;
import com.cartnest.repository.IdCount;
import com.cartnest.repository.ProductRepository;

@Service
@Transactional
public class AdminCatalogService {
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;

    public AdminCatalogService(CategoryRepository categoryRepository, BrandRepository brandRepository,
                               ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
    }

    public AdminCategoryResponse createCategory(CategoryRequest request) {
        Category parent = null;
        if (request.parentId() != null) {
            parent = findCategory(request.parentId());
            if (parent.getParent() != null) {
                throw new BadRequestException("Sub-categories can only be created under a top-level category");
            }
            if (productRepository.existsByCategoryId(parent.getId())) {
                throw new ConflictException("'" + parent.getName() + "' holds products directly, so it cannot get sub-categories");
            }
        }
        String name = request.name().trim();

        String preferred = parent != null && categoryRepository.existsBySlug(Slugs.of(name))
                ? parent.getSlug() + " " + name : name;
        Category category = new Category(name, Slugs.unique(preferred, categoryRepository::existsBySlug), parent);
        category.setImageUrl(request.imageUrl());
        category.setSortOrder(request.sortOrder() == null ? (int) categoryRepository.count() + 1 : request.sortOrder());
        return toResponse(categoryRepository.save(category));
    }

    public AdminCategoryResponse updateCategory(Long id, CategoryRequest request) {
        Category category = findCategory(id);
        category.setName(request.name().trim());
        if (request.imageUrl() != null) {
            category.setImageUrl(request.imageUrl());
        }
        if (request.sortOrder() != null) {
            category.setSortOrder(request.sortOrder());
        }
        return toResponse(category);
    }

    public void deleteCategory(Long id) {
        Category category = findCategory(id);
        if (categoryRepository.existsByParentId(id)) {
            throw new ConflictException("'" + category.getName() + "' still has sub-categories");
        }
        if (productRepository.existsByCategoryId(id)) {
            throw new ConflictException("'" + category.getName() + "' still has products; move or deactivate them first");
        }
        categoryRepository.delete(category);
    }

    @Transactional(readOnly = true)
    public List<BrandResponse> brands() {
        Map<Long, Long> counts = productRepository.countActiveByBrand().stream()
                .collect(Collectors.toMap(IdCount::id, IdCount::total));
        return brandRepository.findAll(Sort.by("name")).stream()
                .map(b -> new BrandResponse(b.getId(), b.getName(), b.getSlug(), counts.getOrDefault(b.getId(), 0L)))
                .toList();
    }

    public BrandResponse createBrand(BrandRequest request) {
        String name = request.name().trim();
        if (brandRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Brand '" + name + "' already exists");
        }
        Brand brand = brandRepository.save(new Brand(name, Slugs.unique(name, brandRepository::existsBySlug)));
        return new BrandResponse(brand.getId(), brand.getName(), brand.getSlug(), 0);
    }

    public BrandResponse renameBrand(Long id, BrandRequest request) {
        Brand brand = findBrand(id);
        String name = request.name().trim();
        if (brandRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("Brand '" + name + "' already exists");
        }
        brand.rename(name, brand.getSlug());
        return new BrandResponse(brand.getId(), brand.getName(), brand.getSlug(), 0);
    }

    public void deleteBrand(Long id) {
        Brand brand = findBrand(id);
        if (productRepository.existsByBrandId(id)) {
            throw new ConflictException("Brand '" + brand.getName() + "' still has products");
        }
        brandRepository.delete(brand);
    }

    private Category findCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category " + id + " not found"));
    }

    private Brand findBrand(Long id) {
        return brandRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Brand " + id + " not found"));
    }

    private static AdminCategoryResponse toResponse(Category c) {
        return new AdminCategoryResponse(c.getId(), c.getName(), c.getSlug(),
                c.getParent() == null ? null : c.getParent().getId(), c.getImageUrl(), c.getSortOrder());
    }
}
