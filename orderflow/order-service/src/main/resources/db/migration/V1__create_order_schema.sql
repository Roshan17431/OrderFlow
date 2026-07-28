CREATE TABLE customer_order (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    subtotal NUMERIC(19, 2) NOT NULL,
    total_amount NUMERIC(19, 2) NOT NULL,
    cart_version BIGINT NOT NULL,
    cancellation_reason VARCHAR(200),
    inventory_reservation_id UUID,
    inventory_expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_order_status CHECK (status IN (
        'INVENTORY_PENDING', 'INVENTORY_RESERVED', 'INVENTORY_REJECTED',
        'PAYMENT_PENDING', 'CANCEL_REQUESTED', 'CANCELLED')),
    CONSTRAINT ck_order_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_order_subtotal_non_negative CHECK (subtotal >= 0),
    CONSTRAINT ck_order_total_non_negative CHECK (total_amount >= 0)
);
CREATE INDEX idx_customer_order_customer_created
    ON customer_order(customer_id, created_at DESC);
CREATE INDEX idx_customer_order_status_created
    ON customer_order(status, created_at DESC);

CREATE TABLE order_item (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES customer_order(id),
    product_id UUID NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    quantity BIGINT NOT NULL,
    unit_price NUMERIC(19, 2) NOT NULL,
    line_total NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    product_version BIGINT,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_order_item_product UNIQUE (order_id, product_id),
    CONSTRAINT ck_order_item_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_order_item_price_non_negative CHECK (unit_price >= 0),
    CONSTRAINT ck_order_item_total_non_negative CHECK (line_total >= 0)
);

CREATE TABLE order_status_history (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES customer_order(id),
    previous_status VARCHAR(32),
    new_status VARCHAR(32) NOT NULL,
    reason_code VARCHAR(120),
    event_id UUID,
    correlation_id VARCHAR(128),
    changed_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_order_history_order_changed
    ON order_status_history(order_id, changed_at, id);

CREATE TABLE idempotency_record (
    id UUID PRIMARY KEY,
    scope VARCHAR(32) NOT NULL,
    actor_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    resource_id UUID,
    response_status INTEGER,
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    CONSTRAINT uq_idempotency_scope_actor_key
        UNIQUE (scope, actor_id, idempotency_key),
    CONSTRAINT ck_idempotency_scope CHECK (scope IN ('CREATE_ORDER', 'CANCEL_ORDER'))
);

CREATE TABLE outbox_event (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    event_version INTEGER NOT NULL,
    topic VARCHAR(255) NOT NULL,
    message_key VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL,
    correlation_id VARCHAR(128),
    causation_id UUID,
    status VARCHAR(16) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    last_error_code VARCHAR(200),
    locked_at TIMESTAMPTZ,
    locked_by VARCHAR(128),
    CONSTRAINT ck_order_outbox_status
        CHECK (status IN ('NEW', 'IN_PROGRESS', 'PUBLISHED', 'FAILED'))
);
CREATE INDEX idx_order_outbox_due ON outbox_event(status, available_at);

CREATE TABLE processed_event (
    event_id UUID NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    aggregate_id UUID NOT NULL,
    consumer_name VARCHAR(120) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    correlation_id VARCHAR(128),
    PRIMARY KEY (event_id, consumer_name)
);
