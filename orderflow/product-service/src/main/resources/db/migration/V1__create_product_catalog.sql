CREATE TABLE categories (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    normalized_name VARCHAR(120) NOT NULL,
    slug VARCHAR(120) NOT NULL,
    description VARCHAR(1000),
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_categories_normalized_name UNIQUE (normalized_name),
    CONSTRAINT uq_categories_slug UNIQUE (slug),
    CONSTRAINT ck_categories_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE products (
    id UUID PRIMARY KEY,
    vendor_id UUID NOT NULL,
    sku VARCHAR(64) NOT NULL,
    slug VARCHAR(120) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(4000),
    price_amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(16) NOT NULL,
    category_id UUID NOT NULL REFERENCES categories(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_products_vendor_sku UNIQUE (vendor_id, sku),
    CONSTRAINT uq_products_slug UNIQUE (slug),
    CONSTRAINT ck_products_price_non_negative CHECK (price_amount >= 0),
    CONSTRAINT ck_products_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_products_status CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'ARCHIVED'))
);

CREATE INDEX idx_products_public_catalog
    ON products (status, category_id, created_at DESC);
CREATE INDEX idx_products_vendor
    ON products (vendor_id, created_at DESC);
CREATE INDEX idx_products_price_currency
    ON products (currency, price_amount);

INSERT INTO categories
    (id, name, normalized_name, slug, description, status, created_at, updated_at, version)
VALUES
    ('10000000-0000-0000-0000-000000000001', 'Electronics', 'electronics',
     'electronics', 'Electronic devices and accessories', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('10000000-0000-0000-0000-000000000002', 'Books', 'books',
     'books', 'Printed and digital reading products', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('10000000-0000-0000-0000-000000000003', 'Home and Kitchen', 'home and kitchen',
     'home-and-kitchen', 'Home and kitchen products', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    ('10000000-0000-0000-0000-000000000004', 'Clothing', 'clothing',
     'clothing', 'Clothing and apparel', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);
