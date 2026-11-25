package com.cartnest.service;

import static com.cartnest.repository.ProductSpecifications.inCategories;
import static com.cartnest.repository.ProductSpecifications.matchesText;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cartnest.dto.SpecResponse;
import com.cartnest.dto.admin.AdminProductDetail;
import com.cartnest.dto.admin.AdminProductRow;
import com.cartnest.dto.admin.AdminVariant;
import com.cartnest.dto.admin.PageResponse;
import com.cartnest.dto.admin.ProductUpsertRequest;
import com.cartnest.dto.admin.SpecInput;
import com.cartnest.dto.admin.VariantInput;
import com.cartnest.entity.Brand;
import com.cartnest.entity.Category;
import com.cartnest.entity.Product;
import com.cartnest.entity.ProductImage;
import com.cartnest.entity.ProductVariant;
import com.cartnest.entity.VariantType;
import com.cartnest.exception.BadRequestException;
import com.cartnest.exception.ConflictException;
import com.cartnest.exception.ResourceNotFoundException;
import com.cartnest.repository.BrandRepository;
import com.cartnest.repository.CategoryRepository;
import com.cartnest.repository.OrderItemRepository;
import com.cartnest.repository.ProductRepository;
import com.cartnest.repository.ProductVariantRepository;

/**
 * Product management for admins. Products are never deleted, only deactivated, because orders point at them.
 * Rules: products sit in a sub-category (a category without children); every variant has price <= MRP, a unique
 * label within the product and a unique SKU; NONE products have exactly one variant; variants that have been ordered
 * cannot be removed.
 */
@Service
@Transactional
public class AdminProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final OrderItemRepository orderItemRepository;
    private final CategoryService categoryService;

    public AdminProductService(ProductRepository productRepository, ProductVariantRepository variantRepository,
                               CategoryRepository categoryRepository, BrandRepository brandRepository,
                               OrderItemRepository orderItemRepository, CategoryService categoryService) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.orderItemRepository = orderItemRepository;
        this.categoryService = categoryService;
    }

    /** All products, active or not. Filters: text, category (with sub-categories), active. Newest changes first. */
    @Transactional(readOnly = true)
    public PageResponse<AdminProductRow> list(String q, String category, Boolean active, int page, int size) {
        checkPaging(page, size);
        List<Specification<Product>> filters = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            filters.add(matchesText(q));
        }
        if (category != null && !category.isBlank()) {
            CategoryTree tree = categoryService.loadTree();
            filters.add(inCategories(tree.subtreeIds(categoryService.findOrThrow(tree, category))));
        }
        if (active != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("active"), active));
        }
        Page<Product> result = productRepository.findAll(Specification.allOf(filters),
                PageRequest.of(page, size, Sort.by("updatedAt").descending().and(Sort.by("id").descending())));
        return new PageResponse<>(result.getContent().stream().map(this::toRow).toList(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AdminProductDetail get(Long id) {
        return toDetail(find(id));
    }

    public AdminProductDetail create(ProductUpsertRequest request) {
        Product product = new Product(Slugs.unique(request.title(), productRepository::existsBySlug));
        apply(product, request);
        productRepository.save(product);
        return toDetail(product);
    }

    public AdminProductDetail update(Long id, ProductUpsertRequest request) {
        Product product = find(id);
        apply(product, request);
        return toDetail(product);
    }

    /** Deactivated products disappear from the shop (listing, search, product page) but stay in orders. */
    public AdminProductDetail setActive(Long id, boolean active) {
        Product product = find(id);
        product.setActive(active);
        return toDetail(product);
    }

    /** Quick restock from the low-stock list. Locks the row like checkout does. */
    public AdminVariant updateStock(Long variantId, int stock) {
        ProductVariant variant = variantRepository.lockAllById(Set.of(variantId)).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Variant " + variantId + " not found"));
        variant.setStock(stock);
        return toVariant(variant);
    }

    private void apply(Product product, ProductUpsertRequest r) {
        Category category = categoryRepository.findById(r.categoryId())
                .orElseThrow(() -> new BadRequestException("Category " + r.categoryId() + " does not exist"));
        if (categoryRepository.existsByParentId(category.getId())) {
            throw new BadRequestException("Products can only be placed in a sub-category; '" + category.getName()
                    + "' has sub-categories");
        }
        Brand brand = r.brandId() == null ? null : brandRepository.findById(r.brandId())
                .orElseThrow(() -> new BadRequestException("Brand " + r.brandId() + " does not exist"));
        checkVariants(r.variantType(), r.variants());

        product.setTitle(r.title().trim());
        product.setDescription(r.description().trim());
        product.setCategory(category);
        product.setBrand(brand);
        product.setVariantType(r.variantType());
        product.setWarranty(blankToNull(r.warranty()));
        product.setShippingInfo(blankToNull(r.shippingInfo()));
        product.setReturnPolicy(blankToNull(r.returnPolicy()));
        if (r.active() != null) {
            product.setActive(r.active());
        }
        product.replaceImages(r.images().stream().map(String::trim).toList());
        List<SpecInput> specs = r.specs() == null ? List.of() : r.specs();
        product.replaceSpecs(specs.stream().map(s -> new String[] { s.name().trim(), s.value().trim() }).toList());
        product.getTags().clear();
        if (r.tags() != null) {
            r.tags().stream().map(t -> t.trim().toLowerCase(Locale.ROOT)).forEach(product.getTags()::add);
        }
        applyVariants(product, r.variants());
        product.refreshPricing();
    }

    /** Variants with an id are updated, without an id are added, and the product's other variants are removed. */
    private void applyVariants(Product product, List<VariantInput> inputs) {
        Map<Long, ProductVariant> existing = product.getVariants().stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
        Set<Long> kept = inputs.stream().map(VariantInput::id).filter(id -> id != null).collect(Collectors.toSet());
        for (Long id : kept) {
            if (!existing.containsKey(id)) {
                throw new BadRequestException("Variant " + id + " does not belong to this product");
            }
        }
        List<ProductVariant> removed = product.getVariants().stream().filter(v -> !kept.contains(v.getId())).toList();
        for (ProductVariant v : removed) {
            if (orderItemRepository.existsByVariantId(v.getId())) {
                throw new ConflictException("Variant '" + v.getLabel() + "' has been ordered and cannot be removed; "
                        + "set its stock to 0 instead");
            }
        }
        product.getVariants().removeAll(removed);
        // Delete before inserting, so a SKU freed by a removed variant can be reused straight away
        productRepository.flush();

        Set<String> ownSkus = product.getVariants().stream().map(v -> v.getSku().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        for (int i = 0; i < inputs.size(); i++) {
            VariantInput in = inputs.get(i);
            String sku = in.sku().trim().toUpperCase(Locale.ROOT);
            ProductVariant variant = in.id() == null ? new ProductVariant(product) : existing.get(in.id());
            boolean skuChanged = variant.getSku() == null || !variant.getSku().equalsIgnoreCase(sku);
            if (skuChanged && !ownSkus.contains(sku) && variantRepository.existsBySkuIgnoreCase(sku)) {
                throw new ConflictException("SKU " + sku + " is already used by another product");
            }
            variant.update(sku, in.label().trim(), in.price(), in.mrp(), in.stock(), i + 1);
            if (in.id() == null) {
                product.getVariants().add(variant);
            }
        }
    }

    private static void checkVariants(VariantType type, List<VariantInput> variants) {
        if (type == VariantType.NONE && variants.size() != 1) {
            throw new BadRequestException("A product without options (variantType NONE) has exactly one variant");
        }
        Set<String> labels = new HashSet<>();
        Set<String> skus = new HashSet<>();
        for (VariantInput v : variants) {
            if (!labels.add(v.label().trim().toLowerCase(Locale.ROOT))) {
                throw new BadRequestException("Variant label '" + v.label().trim() + "' is used twice");
            }
            if (!skus.add(v.sku().trim().toUpperCase(Locale.ROOT))) {
                throw new BadRequestException("SKU " + v.sku().trim().toUpperCase(Locale.ROOT) + " is used twice");
            }
            if (v.mrp().compareTo(v.price()) < 0) {
                throw new BadRequestException("Variant '" + v.label().trim() + "': MRP cannot be lower than the price");
            }
        }
    }

    private Product find(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product " + id + " not found"));
    }

    static void checkPaging(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException("page must be 0 or more and size between 1 and 100");
        }
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private AdminProductRow toRow(Product p) {
        return new AdminProductRow(p.getId(), p.getSlug(), p.getTitle(), p.getBrand() == null ? null : p.getBrand().getName(),
                p.getCategory().getName(), p.getThumbnailUrl(), p.getPrice(), p.getMrp(), p.getDiscountPercent(),
                p.getVariants().stream().mapToInt(ProductVariant::getStock).sum(), p.getVariants().size(), p.isActive(),
                p.getRatingAvg(), p.getRatingCount(), p.getUpdatedAt());
    }

    private static AdminVariant toVariant(ProductVariant v) {
        return new AdminVariant(v.getId(), v.getSku(), v.getLabel(), v.getPrice(), v.getMrp(), v.getStock());
    }

    private AdminProductDetail toDetail(Product p) {
        return new AdminProductDetail(p.getId(), p.getSlug(), p.getTitle(), p.getDescription(), p.getCategory().getId(),
                p.getCategory().getName(), p.getBrand() == null ? null : p.getBrand().getId(),
                p.getBrand() == null ? null : p.getBrand().getName(), p.getVariantType(),
                p.getImages().stream().map(ProductImage::getUrl).toList(),
                p.getVariants().stream().sorted(Comparator.comparingInt(ProductVariant::getPosition))
                        .map(AdminProductService::toVariant).toList(),
                p.getSpecs().stream().map(s -> new SpecResponse(s.getName(), s.getValue())).toList(),
                p.getTags().stream().sorted().toList(), p.getWarranty(), p.getShippingInfo(), p.getReturnPolicy(),
                p.isActive(), p.getPrice(), p.getMrp(), p.getDiscountPercent());
    }
}
