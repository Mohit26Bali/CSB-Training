-- Catalogue: categories (a tree), brands, products with images, variants, specifications and tags.
-- Users and reviews are here too because every imported review belongs to a user.

CREATE TABLE users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(150) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    phone         VARCHAR(15),
    role          VARCHAR(20)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('CUSTOMER', 'ADMIN'))
);

CREATE TABLE categories (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(80)  NOT NULL,
    slug       VARCHAR(80)  NOT NULL,
    parent_id  BIGINT,
    image_url  VARCHAR(500),
    sort_order INT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_categories_slug UNIQUE (slug),
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories (id)
);

CREATE TABLE brands (
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    slug VARCHAR(80) NOT NULL,
    CONSTRAINT uk_brands_name UNIQUE (name),
    CONSTRAINT uk_brands_slug UNIQUE (slug)
);

-- price / mrp / discount_percent are the cheapest variant's values, kept here so listing pages can filter and sort
-- without joining the variants. rating_avg / rating_count are kept in step with the reviews table.
CREATE TABLE products (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    title            VARCHAR(150)  NOT NULL,
    slug             VARCHAR(160)  NOT NULL,
    description      VARCHAR(2000) NOT NULL,
    brand_id         BIGINT,
    category_id      BIGINT        NOT NULL,
    variant_type     VARCHAR(20)   NOT NULL DEFAULT 'NONE',
    price            DECIMAL(10, 2) NOT NULL,
    mrp              DECIMAL(10, 2) NOT NULL,
    discount_percent INT           NOT NULL DEFAULT 0,
    rating_avg       DECIMAL(3, 2) NOT NULL DEFAULT 0,
    rating_count     INT           NOT NULL DEFAULT 0,
    thumbnail_url    VARCHAR(500)  NOT NULL,
    warranty         VARCHAR(100),
    shipping_info    VARCHAR(100),
    return_policy    VARCHAR(100),
    active           BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_products_slug UNIQUE (slug),
    CONSTRAINT fk_products_brand FOREIGN KEY (brand_id) REFERENCES brands (id),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT ck_products_variant_type CHECK (variant_type IN ('NONE', 'SIZE', 'STORAGE', 'CONFIGURATION')),
    CONSTRAINT ck_products_price CHECK (price > 0 AND mrp >= price),
    CONSTRAINT ck_products_discount CHECK (discount_percent BETWEEN 0 AND 99),
    CONSTRAINT ck_products_rating CHECK (rating_avg BETWEEN 0 AND 5)
);

CREATE INDEX ix_products_category ON products (category_id);
CREATE INDEX ix_products_price ON products (price);

CREATE TABLE product_images (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT       NOT NULL,
    url        VARCHAR(500) NOT NULL,
    position   INT          NOT NULL,
    CONSTRAINT fk_product_images_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
);

-- Every product has at least one variant: the thing that is actually stocked and bought (a size, a storage option...)
CREATE TABLE product_variants (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT         NOT NULL,
    sku        VARCHAR(40)    NOT NULL,
    label      VARCHAR(40)    NOT NULL,
    price      DECIMAL(10, 2) NOT NULL,
    mrp        DECIMAL(10, 2) NOT NULL,
    stock      INT            NOT NULL,
    position   INT            NOT NULL,
    CONSTRAINT uk_product_variants_sku UNIQUE (sku),
    CONSTRAINT uk_product_variants_label UNIQUE (product_id, label),
    CONSTRAINT fk_product_variants_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT ck_product_variants_price CHECK (price > 0 AND mrp >= price),
    CONSTRAINT ck_product_variants_stock CHECK (stock >= 0)
);

CREATE TABLE product_specs (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT       NOT NULL,
    name       VARCHAR(60)  NOT NULL,
    value      VARCHAR(200) NOT NULL,
    position   INT          NOT NULL,
    CONSTRAINT fk_product_specs_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
);

CREATE TABLE product_tags (
    product_id BIGINT      NOT NULL,
    tag        VARCHAR(60) NOT NULL,
    PRIMARY KEY (product_id, tag),
    CONSTRAINT fk_product_tags_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
);

-- One review per customer per product
CREATE TABLE reviews (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT        NOT NULL,
    user_id    BIGINT        NOT NULL,
    rating     INT           NOT NULL,
    comment    VARCHAR(1000) NOT NULL,
    created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_reviews_product_user UNIQUE (product_id, user_id),
    CONSTRAINT fk_reviews_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT fk_reviews_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_reviews_rating CHECK (rating BETWEEN 1 AND 5)
);
