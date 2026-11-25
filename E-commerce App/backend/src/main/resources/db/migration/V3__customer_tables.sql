-- A customer's saved delivery addresses, shopping cart and wishlist.

CREATE TABLE addresses (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    full_name    VARCHAR(100) NOT NULL,
    phone        VARCHAR(10)  NOT NULL,
    line1        VARCHAR(150) NOT NULL,
    line2        VARCHAR(150),
    landmark     VARCHAR(100),
    city         VARCHAR(60)  NOT NULL,
    state        VARCHAR(60)  NOT NULL,
    pincode      CHAR(6)      NOT NULL,
    address_type VARCHAR(10)  NOT NULL,
    is_default   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_addresses_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_addresses_type CHECK (address_type IN ('HOME', 'WORK', 'OTHER')),
    CONSTRAINT ck_addresses_pincode CHECK (pincode REGEXP '^[1-9][0-9]{5}$'),
    CONSTRAINT ck_addresses_phone CHECK (phone REGEXP '^[6-9][0-9]{9}$')
);

-- One row per variant in the cart; adding the same variant again raises the quantity instead
CREATE TABLE cart_items (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT   NOT NULL,
    variant_id BIGINT   NOT NULL,
    quantity   INT      NOT NULL,
    added_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cart_items_user_variant UNIQUE (user_id, variant_id),
    CONSTRAINT fk_cart_items_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_items_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id) ON DELETE CASCADE,
    CONSTRAINT ck_cart_items_quantity CHECK (quantity BETWEEN 1 AND 10)
);

-- Wishlists hold products (not variants): the size is chosen when moving to the cart
CREATE TABLE wishlist_items (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT   NOT NULL,
    product_id BIGINT   NOT NULL,
    added_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_wishlist_items_user_product UNIQUE (user_id, product_id),
    CONSTRAINT fk_wishlist_items_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_wishlist_items_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
);
