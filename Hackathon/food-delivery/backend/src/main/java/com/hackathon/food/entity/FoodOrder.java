package com.hackathon.food.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** A customer's order. Prices and totals are fixed when it is placed. */
@Entity
@Table(name = "food_orders",
        indexes = {
                @Index(name = "idx_orders_status_created", columnList = "status, createdAt"),
                @Index(name = "idx_orders_customer", columnList = "customer_id, id")
        },
        // one order per (customer, idempotency key): a retried request with the same key returns the order that already exists
        uniqueConstraints = @UniqueConstraint(name = "uk_orders_customer_key", columnNames = { "customer_id", "idempotencyKey" }))
public class FoodOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrderStatus status = OrderStatus.PLACED;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal tax;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal deliveryFee;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Column(nullable = false, length = 200)
    private String address;

    @Column(nullable = false, length = 15)
    private String phone;

    @Column(length = 200)
    private String notes;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime acceptedAt;

    private String acceptedBy;

    private LocalDateTime preparingAt;

    private LocalDateTime readyAt;

    private LocalDateTime deliveredAt;

    private LocalDateTime cancelledAt;

    private String cancelledBy;

    @Column(length = 300)
    private String cancelReason;

    /** Optional client-generated key (a UUID) that makes "place order" safe to retry. */
    @Column(length = 64)
    private String idempotencyKey;

    protected FoodOrder() {
    }

    public FoodOrder(User customer, BigDecimal subtotal, BigDecimal tax, BigDecimal deliveryFee, String address, String phone, String notes) {
        this(customer, subtotal, tax, deliveryFee, address, phone, notes, LocalDateTime.now());
    }

    /** createdAt is passed in so the service can use the restaurant's clock and time zone. */
    public FoodOrder(User customer, BigDecimal subtotal, BigDecimal tax, BigDecimal deliveryFee, String address, String phone, String notes, LocalDateTime createdAt) {
        this.createdAt = createdAt;
        this.customer = customer;
        this.subtotal = subtotal;
        this.tax = tax;
        this.deliveryFee = deliveryFee;
        this.total = subtotal.add(tax).add(deliveryFee);
        this.address = address;
        this.phone = phone;
        this.notes = notes;
    }

    public void addItem(MenuItem menuItem, int quantity) {
        items.add(new OrderItem(this, menuItem, quantity));
    }

    /** Moves to the given status and stamps the time. */
    public void moveTo(OrderStatus next, String by, LocalDateTime now) {
        switch (next) {
            case ACCEPTED -> {
                acceptedAt = now;
                acceptedBy = by;
            }
            case PREPARING -> preparingAt = now;
            case READY -> readyAt = now;
            case DELIVERED -> deliveredAt = now;
            default -> {
            }
        }
        this.status = next;
    }

    public void cancel(String by, String reason, LocalDateTime now) {
        this.status = OrderStatus.CANCELLED;
        this.cancelledAt = now;
        this.cancelledBy = by;
        this.cancelReason = reason;
    }

    /** Used by the demo data: sets the status and fills the timestamps a real order would have by then. */
    public void forceState(OrderStatus target, LocalDateTime placedAt, String by, String reason) {
        this.createdAt = placedAt;
        this.status = target;
        if (target == OrderStatus.CANCELLED) {
            this.cancelledAt = placedAt.plusMinutes(6);
            this.cancelledBy = by;
            this.cancelReason = reason;
            return;
        }
        int step = target.ordinal();
        if (step >= OrderStatus.ACCEPTED.ordinal()) {
            acceptedAt = placedAt.plusMinutes(3);
            acceptedBy = by;
        }
        if (step >= OrderStatus.PREPARING.ordinal()) {
            preparingAt = placedAt.plusMinutes(8);
        }
        if (step >= OrderStatus.READY.ordinal()) {
            readyAt = placedAt.plusMinutes(28);
        }
        if (step >= OrderStatus.DELIVERED.ordinal()) {
            deliveredAt = placedAt.plusMinutes(52);
        }
    }

    public Long getId() {
        return id;
    }

    public User getCustomer() {
        return customer;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getTax() {
        return tax;
    }

    public BigDecimal getDeliveryFee() {
        return deliveryFee;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getAddress() {
        return address;
    }

    public String getPhone() {
        return phone;
    }

    public String getNotes() {
        return notes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public String getAcceptedBy() {
        return acceptedBy;
    }

    public LocalDateTime getPreparingAt() {
        return preparingAt;
    }

    public LocalDateTime getReadyAt() {
        return readyAt;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public String getCancelledBy() {
        return cancelledBy;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}
