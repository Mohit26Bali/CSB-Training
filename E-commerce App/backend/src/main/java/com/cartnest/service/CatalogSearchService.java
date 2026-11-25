package com.cartnest.service;

import static com.cartnest.repository.ProductSpecifications.active;
import static com.cartnest.repository.ProductSpecifications.availableInSizes;
import static com.cartnest.repository.ProductSpecifications.brandSlugs;
import static com.cartnest.repository.ProductSpecifications.discountAtLeast;
import static com.cartnest.repository.ProductSpecifications.inCategories;
import static com.cartnest.repository.ProductSpecifications.inStock;
import static com.cartnest.repository.ProductSpecifications.matchesText;
import static com.cartnest.repository.ProductSpecifications.orderedByRelevance;
import static com.cartnest.repository.ProductSpecifications.priceAtLeast;
import static com.cartnest.repository.ProductSpecifications.priceAtMost;
import static com.cartnest.repository.ProductSpecifications.ratingAtLeast;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cartnest.dto.BrandRef;
import com.cartnest.dto.CategoryRef;
import com.cartnest.dto.FacetOption;
import com.cartnest.dto.PriceRange;
import com.cartnest.dto.ProductCard;
import com.cartnest.dto.ProductPage;
import com.cartnest.dto.ProductSearchRequest;
import com.cartnest.dto.SearchFacets;
import com.cartnest.dto.SuggestResponse;
import com.cartnest.entity.Product;
import com.cartnest.entity.VariantType;
import com.cartnest.exception.BadRequestException;
import com.cartnest.repository.BrandRepository;
import com.cartnest.repository.ProductRepository;

@Service
@Transactional(readOnly = true)
public class CatalogSearchService {

    private static final List<String> APPAREL_SIZES = List.of("XS", "S", "M", "L", "XL", "XXL");
    private static final List<Integer> RATING_STEPS = List.of(4, 3, 2);
    private static final List<Integer> DISCOUNT_STEPS = List.of(50, 40, 30, 20, 10);

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final CategoryService categoryService;
    private final ProductMapper mapper;

    public CatalogSearchService(ProductRepository productRepository, BrandRepository brandRepository,
                                CategoryService categoryService, ProductMapper mapper) {
        this.productRepository = productRepository;
        this.brandRepository = brandRepository;
        this.categoryService = categoryService;
        this.mapper = mapper;
    }

    /**
     * One page of products for a listing or search page, plus the filter options (facets).
     * The facets are based on the category and search text only, so choosing a brand does not hide the other brands.
     */
    public ProductPage search(ProductSearchRequest request) {
        if (request.minPrice() != null && request.maxPrice() != null
                && request.minPrice().compareTo(request.maxPrice()) > 0) {
            throw new BadRequestException("minPrice cannot be greater than maxPrice");
        }
        CategoryTree tree = categoryService.loadTree();

        List<Specification<Product>> scope = new ArrayList<>();
        scope.add(active());
        if (request.category() != null && !request.category().isBlank()) {
            scope.add(inCategories(tree.subtreeIds(categoryService.findOrThrow(tree, request.category().trim()))));
        }
        if (request.hasText()) {
            scope.add(matchesText(request.q()));
        }

        List<Specification<Product>> filters = new ArrayList<>(scope);
        List<String> brands = cleaned(request.brand(), true);
        if (!brands.isEmpty()) {
            filters.add(brandSlugs(brands));
        }
        if (request.minPrice() != null) {
            filters.add(priceAtLeast(request.minPrice()));
        }
        if (request.maxPrice() != null) {
            filters.add(priceAtMost(request.maxPrice()));
        }
        if (request.minRating() != null) {
            filters.add(ratingAtLeast(request.minRating()));
        }
        if (request.minDiscount() != null) {
            filters.add(discountAtLeast(request.minDiscount()));
        }
        List<String> sizes = cleaned(request.sizes(), false);
        if (!sizes.isEmpty()) {
            filters.add(availableInSizes(sizes));
        }
        if (Boolean.TRUE.equals(request.inStock())) {
            filters.add(inStock());
        }

        String sort = request.sortOrDefault();
        if ("relevance".equals(sort)) {
            filters.add(orderedByRelevance(request.q()));
        }
        PageRequest pageable = PageRequest.of(request.pageOrDefault(), request.sizeOrDefault(), sortFor(sort));
        Page<Product> page = productRepository.findAll(Specification.allOf(filters), pageable);

        List<Product> inScope = productRepository.findAll(Specification.allOf(scope));
        return new ProductPage(page.getContent().stream().map(mapper::toCard).toList(), page.getNumber(),
                page.getSize(), page.getTotalElements(), page.getTotalPages(), sort, facets(inScope));
    }

    /** Up to 6 products, 4 categories and 4 brands matching what the user has typed so far. */
    public SuggestResponse suggest(String text) {
        if (text == null || text.trim().length() < 2) {
            return new SuggestResponse(List.of(), List.of(), List.of());
        }
        String needle = text.trim().toLowerCase(Locale.ROOT);
        List<ProductCard> products = productRepository.findAll(
                        Specification.allOf(active(), matchesText(text), orderedByRelevance(text)),
                        PageRequest.of(0, 6)).getContent().stream()
                .map(mapper::toCard)
                .toList();
        List<CategoryRef> categories = categoryService.loadTree().all().stream()
                .filter(c -> c.getName().toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing(c -> c.getName().length()))
                .limit(4)
                .map(c -> new CategoryRef(c.getName(), c.getSlug()))
                .toList();
        List<BrandRef> brands = brandRepository.findAll().stream()
                .filter(b -> b.getName().toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing(b -> b.getName().length()))
                .limit(4)
                .map(b -> new BrandRef(b.getName(), b.getSlug()))
                .toList();
        return new SuggestResponse(products, categories, brands);
    }

    static Sort sortFor(String sort) {
        Sort byId = Sort.by("id");
        return switch (sort) {
            case "price_asc" -> Sort.by("price").ascending().and(byId);
            case "price_desc" -> Sort.by("price").descending().and(byId);
            case "newest" -> Sort.by("createdAt").descending().and(byId);
            case "discount" -> Sort.by("discountPercent").descending().and(byId);
            case "rating" -> Sort.by("ratingAvg").descending().and(Sort.by("ratingCount").descending()).and(byId);
            // relevance is ordered by its Specification
            case "relevance" -> Sort.unsorted();
            default -> Sort.by("ratingCount").descending().and(Sort.by("ratingAvg").descending()).and(byId);
        };
    }

    private SearchFacets facets(List<Product> products) {
        List<FacetOption> categories = countOptions(products, p -> p.getCategory().getSlug(),
                p -> p.getCategory().getName());
        List<FacetOption> brands = countOptions(products.stream().filter(p -> p.getBrand() != null).toList(),
                p -> p.getBrand().getSlug(), p -> p.getBrand().getName());

        PriceRange price = products.isEmpty() ? null : new PriceRange(
                products.stream().map(Product::getPrice).min(BigDecimal::compareTo).orElseThrow(),
                products.stream().map(Product::getPrice).max(BigDecimal::compareTo).orElseThrow());

        // A size counts for a product when that size is in stock
        Map<String, Long> sizeCounts = products.stream()
                .filter(p -> p.getVariantType() == VariantType.SIZE)
                .flatMap(p -> p.getVariants().stream().filter(v -> v.getStock() > 0).map(v -> v.getLabel()).distinct())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        List<FacetOption> sizes = sizeCounts.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> sizeOrder(e.getKey())))
                .map(e -> new FacetOption(e.getKey(), e.getKey(), e.getValue()))
                .toList();

        List<FacetOption> ratings = RATING_STEPS.stream()
                .map(step -> new FacetOption(String.valueOf(step), step + "★ & above", products.stream()
                        .filter(p -> p.getRatingAvg().doubleValue() >= step).count()))
                .filter(o -> o.count() > 0)
                .toList();
        List<FacetOption> discounts = DISCOUNT_STEPS.stream()
                .map(step -> new FacetOption(String.valueOf(step), step + "% or more", products.stream()
                        .filter(p -> p.getDiscountPercent() >= step).count()))
                .filter(o -> o.count() > 0)
                .toList();
        return new SearchFacets(categories, brands, price, sizes, ratings, discounts);
    }

    /** Groups products by a key and returns the groups, largest first. */
    private List<FacetOption> countOptions(List<Product> products, Function<Product, String> value,
                                           Function<Product, String> label) {
        Map<String, String> labels = products.stream()
                .collect(Collectors.toMap(value, label, (first, second) -> first));
        return products.stream()
                .collect(Collectors.groupingBy(value, Collectors.counting()))
                .entrySet().stream()
                .map(e -> new FacetOption(e.getKey(), labels.get(e.getKey()), e.getValue()))
                .sorted(Comparator.comparingLong(FacetOption::count).reversed()
                        .thenComparing(FacetOption::label, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** S, M, L... in clothing order, then shoe sizes by number (UK 4 before UK 10). */
    private static int sizeOrder(String label) {
        int apparel = APPAREL_SIZES.indexOf(label);
        if (apparel >= 0) {
            return apparel;
        }
        String digits = label.replaceAll("\\D", "");
        return digits.isEmpty() ? 1000 : 100 + Integer.parseInt(digits);
    }

    private static List<String> cleaned(List<String> values, boolean lowerCase) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .map(v -> lowerCase ? v.toLowerCase(Locale.ROOT) : v)
                .distinct()
                .toList();
    }
}
