ALTER TABLE customer_order
    ADD COLUMN payment_id UUID,
    ADD COLUMN paid_at TIMESTAMPTZ,
    ADD COLUMN payment_failed_at TIMESTAMPTZ,
    ADD COLUMN payment_failure_code VARCHAR(80),
    ADD COLUMN refunded_amount NUMERIC(19, 2) NOT NULL DEFAULT 0,
    ADD COLUMN refunded_at TIMESTAMPTZ,
    ADD COLUMN inventory_released_at TIMESTAMPTZ;

ALTER TABLE customer_order DROP CONSTRAINT ck_order_status;
ALTER TABLE customer_order ADD CONSTRAINT ck_order_status CHECK (status IN (
    'INVENTORY_PENDING', 'INVENTORY_RESERVED', 'INVENTORY_REJECTED',
    'PAYMENT_PENDING', 'PAID', 'PAYMENT_FAILED', 'CANCEL_REQUESTED',
    'CANCELLED', 'PARTIALLY_REFUNDED', 'REFUNDED'));
ALTER TABLE customer_order ADD CONSTRAINT ck_order_refunded_amount
    CHECK (refunded_amount >= 0 AND refunded_amount <= total_amount);

CREATE INDEX idx_customer_order_payment_id
    ON customer_order(payment_id) WHERE payment_id IS NOT NULL;
