package com.cartnest.service;

import static com.cartnest.repository.ProductSpecifications.active;
import static com.cartnest.repository.ProductSpecifications.discountAtLeast;
import static com.cartnest.repository.ProductSpecifications.inCategories;
import static com.cartnest.repository.ProductSpecifications.inStock;
import static com.cartnest.repository.ProductSpecifications.ratingAtLeast;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cartnest.dto.HomeResponse;
import com.cartnest.dto.HomeSection;
import com.cartnest.dto.ProductCard;
import com.cartnest.entity.Category;
import com.cartnest.entity.Product;
import com.cartnest.repository.ProductRepository;

@Service
@Transactional(readOnly = true)
public class HomeService {
    private static final int RAIL_SIZE = 12;

    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    private final ProductMapper mapper;

    public HomeService(ProductRepository productRepository, CategoryService categoryService, ProductMapper mapper) {
        this.productRepository = productRepository;
        this.categoryService = categoryService;
        this.mapper = mapper;
    }

    public HomeResponse getHome() {
        CategoryTree tree = categoryService.loadTree();
        List<HomeSection> sections = new ArrayList<>();
        sections.add(new HomeSection("deals", "Deals of the Day", "sort=discount&minDiscount=15",
                rail(Specification.allOf(active(), inStock(), discountAtLeast(15)), "discount")));
        sections.add(new HomeSection("top-rated", "Top Rated", "minRating=4&sort=rating",
                rail(Specification.allOf(active(), ratingAtLeast(4)), "rating")));
        sections.add(new HomeSection("new", "New Arrivals", "sort=newest",
                rail(active(), "newest")));
        for (Category department : tree.roots()) {
            List<ProductCard> products = rail(Specification.allOf(active(),
                    inCategories(tree.subtreeIds(department))), "popularity");
            if (!products.isEmpty()) {
                sections.add(new HomeSection(department.getSlug(), "Best of " + department.getName(),
                        "category=" + department.getSlug(), products));
            }
        }
        return new HomeResponse(tree.roots().stream().map(tree::toNode).toList(), sections);
    }

    private List<ProductCard> rail(Specification<Product> spec, String sort) {
        return productRepository.findAll(spec, PageRequest.of(0, RAIL_SIZE, CatalogSearchService.sortFor(sort)))
                .getContent().stream().map(mapper::toCard).toList();
    }
}
