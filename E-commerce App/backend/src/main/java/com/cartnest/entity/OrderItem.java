package com.cartnest.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One line of an order. Title, size and prices are copies taken when the order was placed. */
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Column(name = "product_title", nullable = false, length = 150)
    private String productTitle;

    @Column(name = "product_slug", nullable = false, length = 160)
    private String productSlug;

    @Column(name = "variant_label", nullable = false, length = 40)
    private String variantLabel;

    @Column(name = "thumbnail_url", nullable = false, length = 500)
    private String thumbnailUrl;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "unit_mrp", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitMrp;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    protected OrderItem() {
    }

    public OrderItem(CustomerOrder order, ProductVariant variant, int quantity) {
        Product p = variant.getProduct();
        this.order = order;
        this.product = p;
        this.variant = variant;
        this.productTitle = p.getTitle();
        this.productSlug = p.getSlug();
        this.variantLabel = variant.getLabel();
        this.thumbnailUrl = p.getThumbnailUrl();
        this.unitPrice = variant.getPrice();
        this.unitMrp = variant.getMrp();
        this.quantity = quantity;
        this.lineTotal = variant.getPrice().multiply(BigDecimal.valueOf(quantity));
    }

    public Long getId() {
        return id;
    }

    public CustomerOrder getOrder() {
        return order;
    }

    public Product getProduct() {
        return product;
    }

    public ProductVariant getVariant() {
        return variant;
    }

    public String getProductTitle() {
        return productTitle;
    }

    public String getProductSlug() {
        return productSlug;
    }

    public String getVariantLabel() {
        return variantLabel;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getUnitMrp() {
        return unitMrp;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }
}
