CREATE TABLE inventory_item (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL UNIQUE,
    on_hand_quantity BIGINT NOT NULL,
    reserved_quantity BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_inventory_on_hand_non_negative CHECK (on_hand_quantity >= 0),
    CONSTRAINT ck_inventory_reserved_non_negative CHECK (reserved_quantity >= 0),
    CONSTRAINT ck_inventory_reserved_within_on_hand CHECK (reserved_quantity <= on_hand_quantity)
);

CREATE TABLE inventory_adjustment (
    id UUID PRIMARY KEY,
    inventory_item_id UUID NOT NULL REFERENCES inventory_item(id),
    product_id UUID NOT NULL,
    quantity_delta BIGINT NOT NULL,
    resulting_on_hand BIGINT NOT NULL,
    resulting_reserved BIGINT NOT NULL,
    reason VARCHAR(120) NOT NULL,
    reference VARCHAR(200),
    actor_user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    correlation_id VARCHAR(128),
    CONSTRAINT ck_inventory_adjustment_non_zero CHECK (quantity_delta <> 0)
);
CREATE INDEX idx_inventory_adjustment_product_created
    ON inventory_adjustment(product_id, created_at DESC);

CREATE TABLE inventory_reservation (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL,
    expires_at TIMESTAMPTZ,
    failure_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    released_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_inventory_reservation_status
        CHECK (status IN ('RESERVED', 'REJECTED', 'RELEASED', 'EXPIRED'))
);
CREATE INDEX idx_inventory_reservation_expiration
    ON inventory_reservation(status, expires_at);

CREATE TABLE inventory_reservation_item (
    id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL REFERENCES inventory_reservation(id),
    product_id UUID NOT NULL,
    quantity BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_inventory_reservation_product UNIQUE (reservation_id, product_id),
    CONSTRAINT ck_inventory_reservation_quantity_positive CHECK (quantity > 0)
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
    CONSTRAINT ck_inventory_outbox_status
        CHECK (status IN ('NEW', 'IN_PROGRESS', 'PUBLISHED', 'FAILED'))
);
CREATE INDEX idx_inventory_outbox_due ON outbox_event(status, available_at);

CREATE TABLE processed_event (
    event_id UUID NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    aggregate_id UUID NOT NULL,
    consumer_name VARCHAR(120) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    correlation_id VARCHAR(128),
    PRIMARY KEY (event_id, consumer_name)
);
