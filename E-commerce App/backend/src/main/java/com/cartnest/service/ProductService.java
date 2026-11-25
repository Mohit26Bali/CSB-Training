package com.cartnest.service;

import static com.cartnest.repository.ProductSpecifications.active;
import static com.cartnest.repository.ProductSpecifications.inCategories;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cartnest.dto.BrandRef;
import com.cartnest.dto.ProductCard;
import com.cartnest.dto.ProductDetail;
import com.cartnest.dto.RatingSummary;
import com.cartnest.dto.ReviewPage;
import com.cartnest.dto.ReviewResponse;
import com.cartnest.dto.SpecResponse;
import com.cartnest.entity.Product;
import com.cartnest.entity.ProductImage;
import com.cartnest.entity.Review;
import com.cartnest.exception.BadRequestException;
import com.cartnest.exception.ResourceNotFoundException;
import com.cartnest.repository.IdCount;
import com.cartnest.repository.ProductRepository;
import com.cartnest.repository.ReviewRepository;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private static final int SIMILAR_PRODUCTS = 8;
    private static final int TOP_REVIEWS = 5;

    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;
    private final CategoryService categoryService;
    private final ProductMapper mapper;

    public ProductService(ProductRepository productRepository, ReviewRepository reviewRepository,
                          CategoryService categoryService, ProductMapper mapper) {
        this.productRepository = productRepository;
        this.reviewRepository = reviewRepository;
        this.categoryService = categoryService;
        this.mapper = mapper;
    }

    public ProductDetail getDetail(String slug) {
        Product p = findActive(slug);
        CategoryTree tree = categoryService.loadTree();
        Page<Review> latest = reviewRepository.findByProduct(p,
                PageRequest.of(0, TOP_REVIEWS, Sort.by("createdAt").descending().and(Sort.by("id"))));

        // Similar = most popular other products of the same category
        Specification<Product> sameCategory = Specification.allOf(active(),
                inCategories(Set.of(p.getCategory().getId())),
                (root, query, cb) -> cb.notEqual(root.get("id"), p.getId()));
        List<ProductCard> similar = productRepository.findAll(sameCategory,
                        PageRequest.of(0, SIMILAR_PRODUCTS, CatalogSearchService.sortFor("popularity")))
                .getContent().stream().map(mapper::toCard).toList();

        return new ProductDetail(p.getId(), p.getSlug(), p.getTitle(), p.getDescription(),
                p.getBrand() == null ? null : new BrandRef(p.getBrand().getName(), p.getBrand().getSlug()),
                tree.breadcrumb(p.getCategory().getId()), p.getVariantType(), p.getPrice(), p.getMrp(),
                p.getDiscountPercent(), p.isInStock(),
                p.getImages().stream().map(ProductImage::getUrl).toList(),
                p.getVariants().stream().map(mapper::toVariant).toList(),
                p.getSpecs().stream().map(s -> new SpecResponse(s.getName(), s.getValue())).toList(),
                p.getWarranty(), p.getShippingInfo(), p.getReturnPolicy(),
                p.getTags().stream().sorted().toList(),
                ratingSummary(p), latest.getContent().stream().map(mapper::toReview).toList(), similar);
    }

    /** Reviews page by page; sort is newest (default), highest or lowest. */
    public ReviewPage getReviews(String slug, int page, int size, String sort) {
        if (page < 0) {
            throw new BadRequestException("page must be 0 or more");
        }
        if (size < 1 || size > 50) {
            throw new BadRequestException("size must be between 1 and 50");
        }
        Sort order = switch (sort == null ? "newest" : sort) {
            case "newest" -> Sort.by("createdAt").descending();
            case "highest" -> Sort.by("rating").descending().and(Sort.by("createdAt").descending());
            case "lowest" -> Sort.by("rating").ascending().and(Sort.by("createdAt").descending());
            default -> throw new BadRequestException("sort must be one of newest, highest, lowest");
        };
        Product product = findActive(slug);
        Page<Review> reviews = reviewRepository.findByProduct(product, PageRequest.of(page, size, order.and(Sort.by("id"))));
        List<ReviewResponse> items = reviews.getContent().stream().map(mapper::toReview).toList();
        return new ReviewPage(items, reviews.getNumber(), reviews.getSize(), reviews.getTotalElements(),
                reviews.getTotalPages(), ratingSummary(product));
    }

    private Product findActive(String slug) {
        return productRepository.findBySlugAndActiveTrue(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Product '" + slug + "' not found"));
    }

    /** Average, total and a 5-to-1 star breakdown that always lists all five ratings. */
    private RatingSummary ratingSummary(Product product) {
        Map<Long, Long> counts = reviewRepository.countByRating(product).stream()
                .collect(Collectors.toMap(IdCount::id, IdCount::total));
        Map<Integer, Long> distribution = IntStream.iterate(5, star -> star >= 1, star -> star - 1).boxed()
                .collect(Collectors.toMap(star -> star, star -> counts.getOrDefault((long) star, 0L),
                        (a, b) -> a, LinkedHashMap::new));
        BigDecimal average = product.getRatingAvg();
        return new RatingSummary(average, product.getRatingCount(), distribution);
    }
}
