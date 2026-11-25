-- Coupons, orders (with a copy of the delivery address and of each item's price) and the order status history.

CREATE TABLE coupons (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    code             VARCHAR(20)    NOT NULL,
    description      VARCHAR(200)   NOT NULL,
    discount_type    VARCHAR(10)    NOT NULL,
    discount_value   DECIMAL(10, 2) NOT NULL,
    max_discount     DECIMAL(10, 2),
    min_order_amount DECIMAL(10, 2) NOT NULL DEFAULT 0,
    valid_from       DATETIME       NOT NULL,
    valid_until      DATETIME       NOT NULL,
    usage_limit      INT,
    per_user_limit   INT            NOT NULL DEFAULT 1,
    used_count       INT            NOT NULL DEFAULT 0,
    active           BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_coupons_code UNIQUE (code),
    CONSTRAINT ck_coupons_type CHECK (discount_type IN ('PERCENT', 'FLAT')),
    CONSTRAINT ck_coupons_value CHECK (discount_value > 0 AND (discount_type = 'FLAT' OR discount_value <= 90)),
    CONSTRAINT ck_coupons_dates CHECK (valid_until > valid_from),
    CONSTRAINT ck_coupons_limits CHECK (per_user_limit >= 1 AND used_count >= 0
        AND (usage_limit IS NULL OR used_count <= usage_limit))
);

-- The address is copied into the order: editing or deleting the address later must not change where it was sent
CREATE TABLE orders (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_number      VARCHAR(20)    NOT NULL,
    user_id           BIGINT         NOT NULL,
    status            VARCHAR(20)    NOT NULL,
    payment_method    VARCHAR(10)    NOT NULL,
    payment_status    VARCHAR(10)    NOT NULL,
    payment_reference VARCHAR(40),
    ship_name         VARCHAR(100)   NOT NULL,
    ship_phone        VARCHAR(10)    NOT NULL,
    ship_line1        VARCHAR(150)   NOT NULL,
    ship_line2        VARCHAR(150),
    ship_landmark     VARCHAR(100),
    ship_city         VARCHAR(60)    NOT NULL,
    ship_state        VARCHAR(60)    NOT NULL,
    ship_pincode      CHAR(6)        NOT NULL,
    total_mrp         DECIMAL(12, 2) NOT NULL,
    item_total        DECIMAL(12, 2) NOT NULL,
    coupon_code       VARCHAR(20),
    coupon_discount   DECIMAL(10, 2) NOT NULL DEFAULT 0,
    delivery_fee      DECIMAL(10, 2) NOT NULL,
    grand_total       DECIMAL(12, 2) NOT NULL,
    cancel_reason     VARCHAR(200),
    placed_at         DATETIME       NOT NULL,
    updated_at        DATETIME       NOT NULL,
    CONSTRAINT uk_orders_number UNIQUE (order_number),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_orders_status CHECK (status IN ('PLACED', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT ck_orders_payment_method CHECK (payment_method IN ('COD', 'UPI', 'CARD')),
    CONSTRAINT ck_orders_payment_status CHECK (payment_status IN ('PENDING', 'PAID', 'REFUNDED', 'CANCELLED')),
    CONSTRAINT ck_orders_totals CHECK (grand_total = item_total - coupon_discount + delivery_fee
        AND coupon_discount <= item_total AND item_total <= total_mrp)
);

CREATE INDEX ix_orders_user_placed ON orders (user_id, placed_at);

-- Title, size and prices are copied at the time of ordering, so later catalogue changes do not rewrite history
CREATE TABLE order_items (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id      BIGINT         NOT NULL,
    product_id    BIGINT         NOT NULL,
    variant_id    BIGINT         NOT NULL,
    product_title VARCHAR(150)   NOT NULL,
    product_slug  VARCHAR(160)   NOT NULL,
    variant_label VARCHAR(40)    NOT NULL,
    thumbnail_url VARCHAR(500)   NOT NULL,
    unit_price    DECIMAL(10, 2) NOT NULL,
    unit_mrp      DECIMAL(10, 2) NOT NULL,
    quantity      INT            NOT NULL,
    line_total    DECIMAL(12, 2) NOT NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_order_items_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id),
    CONSTRAINT ck_order_items_quantity CHECK (quantity BETWEEN 1 AND 10),
    CONSTRAINT ck_order_items_total CHECK (line_total = unit_price * quantity)
);

-- Every status the order has been through, for the tracking timeline
CREATE TABLE order_status_history (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id   BIGINT       NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    note       VARCHAR(200),
    changed_by VARCHAR(150) NOT NULL,
    changed_at DATETIME     NOT NULL,
    CONSTRAINT fk_order_status_history_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
);

-- Which customer used which coupon on which order (for the per-customer limit); removed when that order is cancelled
CREATE TABLE coupon_redemptions (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    coupon_id   BIGINT   NOT NULL,
    user_id     BIGINT   NOT NULL,
    order_id    BIGINT   NOT NULL,
    redeemed_at DATETIME NOT NULL,
    CONSTRAINT uk_coupon_redemptions_order UNIQUE (order_id),
    CONSTRAINT fk_coupon_redemptions_coupon FOREIGN KEY (coupon_id) REFERENCES coupons (id),
    CONSTRAINT fk_coupon_redemptions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_coupon_redemptions_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
);

INSERT INTO coupons (code, description, discount_type, discount_value, max_discount, min_order_amount, valid_from,
                     valid_until, usage_limit, per_user_limit) VALUES
('WELCOME10', '10% off up to ₹500 on orders of ₹999 or more', 'PERCENT', 10, 500, 999,
 '2026-01-01 00:00:00', '2027-12-31 23:59:59', NULL, 1),
('FLAT200', '₹200 off on orders of ₹1,499 or more', 'FLAT', 200, NULL, 1499,
 '2026-01-01 00:00:00', '2027-12-31 23:59:59', NULL, 3),
('BIGSAVE1000', '₹1,000 off on orders of ₹9,999 or more', 'FLAT', 1000, NULL, 9999,
 '2026-01-01 00:00:00', '2027-12-31 23:59:59', 500, 1),
('MEGA25', '25% off up to ₹5,000 on orders of ₹19,999 or more (first 100 orders)', 'PERCENT', 25, 5000, 19999,
 '2026-01-01 00:00:00', '2027-12-31 23:59:59', 100, 1),
('NEWYEAR26', '15% off up to ₹750, New Year sale', 'PERCENT', 15, 750, 499,
 '2026-01-01 00:00:00', '2026-01-15 23:59:59', NULL, 1);
