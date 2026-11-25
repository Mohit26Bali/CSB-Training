package com.cartnest.entity;

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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/** An order. Named CustomerOrder because ORDER is a reserved word in SQL and JPQL; the table is "orders". */
@Entity
@Table(name = "orders")
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 20)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PLACED;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 10)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 10)
    private PaymentStatus paymentStatus;

    @Column(name = "payment_reference", length = 40)
    private String paymentReference;

    @Column(name = "ship_name", nullable = false, length = 100)
    private String shipName;

    @Column(name = "ship_phone", nullable = false, length = 10)
    private String shipPhone;

    @Column(name = "ship_line1", nullable = false, length = 150)
    private String shipLine1;

    @Column(name = "ship_line2", length = 150)
    private String shipLine2;

    @Column(name = "ship_landmark", length = 100)
    private String shipLandmark;

    @Column(name = "ship_city", nullable = false, length = 60)
    private String shipCity;

    @Column(name = "ship_state", nullable = false, length = 60)
    private String shipState;

    @Column(name = "ship_pincode", nullable = false, length = 6, columnDefinition = "char(6)")
    private String shipPincode;

    @Column(name = "total_mrp", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalMrp;

    @Column(name = "item_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal itemTotal;

    @Column(name = "coupon_code", length = 20)
    private String couponCode;

    @Column(name = "coupon_discount", nullable = false, precision = 10, scale = 2)
    private BigDecimal couponDiscount = BigDecimal.ZERO;

    @Column(name = "delivery_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal deliveryFee;

    @Column(name = "grand_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal grandTotal;

    @Column(name = "cancel_reason", length = 200)
    private String cancelReason;

    @Column(name = "placed_at", nullable = false)
    private LocalDateTime placedAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    @OrderBy("changedAt, id")
    private List<OrderStatusHistory> history = new ArrayList<>();

    protected CustomerOrder() {
    }

    public CustomerOrder(String orderNumber, User user, PaymentMethod paymentMethod, Address address) {
        this.orderNumber = orderNumber;
        this.user = user;
        this.paymentMethod = paymentMethod;
        this.paymentStatus = PaymentStatus.PENDING;
        this.shipName = address.getFullName();
        this.shipPhone = address.getPhone();
        this.shipLine1 = address.getLine1();
        this.shipLine2 = address.getLine2();
        this.shipLandmark = address.getLandmark();
        this.shipCity = address.getCity();
        this.shipState = address.getState();
        this.shipPincode = address.getPincode();
    }

    public void addItem(ProductVariant variant, int quantity) {
        items.add(new OrderItem(this, variant, quantity));
    }

    /** Moves the order to a new status and records the step in its timeline. */
    public void moveTo(OrderStatus newStatus, String note, String changedBy) {
        moveTo(newStatus, note, changedBy, LocalDateTime.now());
    }

    /** Same, at a given time: used by the demo-data seeder to build past orders. */
    public void moveTo(OrderStatus newStatus, String note, String changedBy, LocalDateTime at) {
        this.status = newStatus;
        this.updatedAt = at;
        history.add(new OrderStatusHistory(this, newStatus, note, changedBy, at));
    }

    /** Demo data only: an order placed in the past. */
    public void backdate(LocalDateTime placedAt) {
        this.placedAt = placedAt;
        this.updatedAt = placedAt;
    }

    /** Orders can be cancelled until they are shipped. */
    public boolean isCancellable() {
        return status == OrderStatus.PLACED || status == OrderStatus.CONFIRMED;
    }

    public void setTotals(BigDecimal totalMrp, BigDecimal itemTotal, String couponCode, BigDecimal couponDiscount,
                          BigDecimal deliveryFee) {
        this.totalMrp = totalMrp;
        this.itemTotal = itemTotal;
        this.couponCode = couponCode;
        this.couponDiscount = couponDiscount;
        this.deliveryFee = deliveryFee;
        this.grandTotal = itemTotal.subtract(couponDiscount).add(deliveryFee);
    }

    public void markPaid(String reference) {
        this.paymentStatus = PaymentStatus.PAID;
        this.paymentReference = reference;
    }

    public void setPaymentStatus(PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public Long getId() {
        return id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public User getUser() {
        return user;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public String getShipName() {
        return shipName;
    }

    public String getShipPhone() {
        return shipPhone;
    }

    public String getShipLine1() {
        return shipLine1;
    }

    public String getShipLine2() {
        return shipLine2;
    }

    public String getShipLandmark() {
        return shipLandmark;
    }

    public String getShipCity() {
        return shipCity;
    }

    public String getShipState() {
        return shipState;
    }

    public String getShipPincode() {
        return shipPincode;
    }

    public BigDecimal getTotalMrp() {
        return totalMrp;
    }

    public BigDecimal getItemTotal() {
        return itemTotal;
    }

    public String getCouponCode() {
        return couponCode;
    }

    public BigDecimal getCouponDiscount() {
        return couponDiscount;
    }

    public BigDecimal getDeliveryFee() {
        return deliveryFee;
    }

    public BigDecimal getGrandTotal() {
        return grandTotal;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public LocalDateTime getPlacedAt() {
        return placedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public List<OrderStatusHistory> getHistory() {
        return history;
    }
}
