CREATE TABLE payment (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE,
    customer_id UUID NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    provider_payment_id VARCHAR(160) UNIQUE,
    provider_client_reference VARCHAR(160),
    payment_deadline TIMESTAMPTZ NOT NULL,
    failure_code VARCHAR(80),
    failure_category VARCHAR(48),
    refunded_amount NUMERIC(19, 2) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    succeeded_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_payment_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_payment_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_payment_refunded_amount CHECK (
        refunded_amount >= 0 AND refunded_amount <= amount),
    CONSTRAINT ck_payment_status CHECK (status IN (
        'PENDING', 'REQUIRES_ACTION', 'PROCESSING', 'SUCCEEDED', 'FAILED',
        'CANCEL_REQUESTED', 'CANCELLED', 'REFUND_PENDING',
        'PARTIALLY_REFUNDED', 'REFUNDED'))
);
CREATE INDEX idx_payment_customer_created ON payment(customer_id, created_at DESC);
CREATE INDEX idx_payment_status_created ON payment(status, created_at DESC);

CREATE TABLE payment_attempt (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payment(id),
    attempt_number INTEGER NOT NULL,
    provider VARCHAR(32) NOT NULL,
    provider_attempt_id VARCHAR(160),
    status VARCHAR(24) NOT NULL,
    failure_code VARCHAR(80),
    failure_category VARCHAR(48),
    retryable BOOLEAN,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    correlation_id VARCHAR(128),
    CONSTRAINT uq_payment_attempt_number UNIQUE (payment_id, attempt_number),
    CONSTRAINT ck_payment_attempt_number CHECK (attempt_number > 0),
    CONSTRAINT ck_payment_attempt_status CHECK (status IN (
        'CREATED', 'SUBMITTED', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'CANCELLED'))
);
CREATE INDEX idx_payment_attempt_payment ON payment_attempt(payment_id, attempt_number);

CREATE TABLE refund (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payment(id),
    order_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(24) NOT NULL,
    reason_code VARCHAR(80) NOT NULL,
    provider_refund_id VARCHAR(160) UNIQUE,
    failure_code VARCHAR(80),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    succeeded_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_refund_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_refund_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_refund_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'CANCELLED'))
);
CREATE INDEX idx_refund_payment_created ON refund(payment_id, created_at DESC);

CREATE TABLE provider_webhook_event (
    id UUID PRIMARY KEY,
    provider VARCHAR(32) NOT NULL,
    provider_event_id VARCHAR(160) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    payment_id UUID,
    refund_id UUID,
    received_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    processing_status VARCHAR(24) NOT NULL,
    failure_code VARCHAR(80),
    CONSTRAINT uq_webhook_provider_event UNIQUE (provider, provider_event_id),
    CONSTRAINT ck_webhook_processing_status CHECK (
        processing_status IN ('RECEIVED', 'PROCESSED', 'FAILED'))
);

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
    CONSTRAINT uq_payment_idempotency UNIQUE (scope, actor_id, idempotency_key),
    CONSTRAINT ck_payment_idempotency_scope CHECK (
        scope IN ('CONFIRM_PAYMENT', 'CANCEL_PAYMENT', 'CREATE_REFUND'))
);

CREATE TABLE payment_cancellation_tombstone (
    order_id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    payment_id UUID,
    reason_code VARCHAR(80) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    correlation_id VARCHAR(128) NOT NULL
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
    CONSTRAINT ck_payment_outbox_status CHECK (
        status IN ('NEW', 'IN_PROGRESS', 'PUBLISHED', 'FAILED'))
);
CREATE INDEX idx_payment_outbox_due ON outbox_event(status, available_at);

CREATE TABLE processed_event (
    event_id UUID NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    aggregate_id UUID NOT NULL,
    consumer_name VARCHAR(120) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    correlation_id VARCHAR(128),
    PRIMARY KEY (event_id, consumer_name)
);
