package com.cartnest.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, unique = true, length = 160)
    private String slug;

    @Column(nullable = false, length = 2000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private Brand brand;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "variant_type", nullable = false, length = 20)
    private VariantType variantType = VariantType.NONE;

    // The cheapest variant's price and MRP, copied here for filtering and sorting listing pages
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal mrp;

    @Column(name = "discount_percent", nullable = false)
    private int discountPercent;

    // Kept in step with the reviews table
    @Column(name = "rating_avg", nullable = false, precision = 3, scale = 2)
    private BigDecimal ratingAvg = BigDecimal.ZERO;

    @Column(name = "rating_count", nullable = false)
    private int ratingCount;

    @Column(name = "thumbnail_url", nullable = false, length = 500)
    private String thumbnailUrl;

    @Column(length = 100)
    private String warranty;

    @Column(name = "shipping_info", length = 100)
    private String shippingInfo;

    @Column(name = "return_policy", length = 100)
    private String returnPolicy;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    // Images, variants and specs are saved and deleted together with the product
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<ProductImage> images = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<ProductVariant> variants = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<ProductSpec> specs = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "product_tags", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "tag", length = 60)
    private Set<String> tags = new LinkedHashSet<>();

    protected Product() {
    }

    public Product(String slug) {
        this.slug = slug;
    }

    public boolean isInStock() {
        return variants.stream().anyMatch(v -> v.getStock() > 0);
    }

    /** Copies the cheapest variant's price and MRP to the product and works out the discount. */
    public void refreshPricing() {
        ProductVariant cheapest = variants.stream()
                .min(Comparator.comparing(ProductVariant::getPrice))
                .orElseThrow(() -> new IllegalStateException("A product needs at least one variant"));
        this.price = cheapest.getPrice();
        this.mrp = cheapest.getMrp();
        this.discountPercent = mrp.compareTo(price) <= 0 ? 0
                : mrp.subtract(price).multiply(BigDecimal.valueOf(100)).divide(mrp, 0, RoundingMode.FLOOR).intValue();
        this.updatedAt = LocalDateTime.now();
    }

    /** Replaces the gallery; the first image is also the thumbnail. */
    public void replaceImages(List<String> urls) {
        images.clear();
        for (int i = 0; i < urls.size(); i++) {
            images.add(new ProductImage(this, urls.get(i), i + 1));
        }
        this.thumbnailUrl = urls.get(0);
    }

    public void replaceSpecs(List<String[]> nameValuePairs) {
        specs.clear();
        for (int i = 0; i < nameValuePairs.size(); i++) {
            specs.add(new ProductSpec(this, nameValuePairs.get(i)[0], nameValuePairs.get(i)[1], i + 1));
        }
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setBrand(Brand brand) {
        this.brand = brand;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public void setVariantType(VariantType variantType) {
        this.variantType = variantType;
    }

    public void setWarranty(String warranty) {
        this.warranty = warranty;
    }

    public void setShippingInfo(String shippingInfo) {
        this.shippingInfo = shippingInfo;
    }

    public void setReturnPolicy(String returnPolicy) {
        this.returnPolicy = returnPolicy;
    }

    public void setActive(boolean active) {
        this.active = active;
        this.updatedAt = LocalDateTime.now();
    }

    /** Called when a review is added: the average and count shown on listing pages. */
    public void setRating(BigDecimal ratingAvg, int ratingCount) {
        this.ratingAvg = ratingAvg;
        this.ratingCount = ratingCount;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getSlug() {
        return slug;
    }

    public String getDescription() {
        return description;
    }

    public Brand getBrand() {
        return brand;
    }

    public Category getCategory() {
        return category;
    }

    public VariantType getVariantType() {
        return variantType;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getMrp() {
        return mrp;
    }

    public int getDiscountPercent() {
        return discountPercent;
    }

    public BigDecimal getRatingAvg() {
        return ratingAvg;
    }

    public int getRatingCount() {
        return ratingCount;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public String getWarranty() {
        return warranty;
    }

    public String getShippingInfo() {
        return shippingInfo;
    }

    public String getReturnPolicy() {
        return returnPolicy;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<ProductImage> getImages() {
        return images;
    }

    public List<ProductVariant> getVariants() {
        return variants;
    }

    public List<ProductSpec> getSpecs() {
        return specs;
    }

    public Set<String> getTags() {
        return tags;
    }
}
