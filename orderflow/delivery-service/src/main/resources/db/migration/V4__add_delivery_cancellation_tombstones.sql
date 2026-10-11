CREATE TABLE delivery_cancellation (order_id UUID PRIMARY KEY, customer_id UUID NOT NULL, reason_code VARCHAR(80) NOT NULL, created_at TIMESTAMPTZ NOT NULL);
