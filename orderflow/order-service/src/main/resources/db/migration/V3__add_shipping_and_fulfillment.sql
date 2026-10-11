ALTER TABLE customer_order
    ADD COLUMN fulfillment_status VARCHAR(40) NOT NULL DEFAULT 'NOT_REQUESTED',
    ADD COLUMN recipient_name VARCHAR(120),
    ADD COLUMN address_line_1 VARCHAR(200),
    ADD COLUMN address_line_2 VARCHAR(200),
    ADD COLUMN city VARCHAR(120),
    ADD COLUMN state_or_region VARCHAR(120),
    ADD COLUMN postal_code VARCHAR(32),
    ADD COLUMN country_code VARCHAR(2),
    ADD COLUMN delivery_instructions VARCHAR(300),
    ADD COLUMN delivery_id UUID,
    ADD COLUMN delivery_agent_id UUID,
    ADD COLUMN delivery_requested_at TIMESTAMPTZ,
    ADD COLUMN delivery_assigned_at TIMESTAMPTZ,
    ADD COLUMN picked_up_at TIMESTAMPTZ,
    ADD COLUMN out_for_delivery_at TIMESTAMPTZ,
    ADD COLUMN delivered_at TIMESTAMPTZ,
    ADD COLUMN delivery_failed_at TIMESTAMPTZ,
    ADD COLUMN delivery_cancelled_at TIMESTAMPTZ;

UPDATE customer_order SET recipient_name = 'Legacy recipient', address_line_1 = 'Legacy address',
    city = 'Unknown', state_or_region = 'Unknown', postal_code = '00000', country_code = 'US'
WHERE recipient_name IS NULL;

ALTER TABLE customer_order ALTER COLUMN recipient_name SET NOT NULL;
ALTER TABLE customer_order ALTER COLUMN address_line_1 SET NOT NULL;
ALTER TABLE customer_order ALTER COLUMN city SET NOT NULL;
ALTER TABLE customer_order ALTER COLUMN state_or_region SET NOT NULL;
ALTER TABLE customer_order ALTER COLUMN postal_code SET NOT NULL;
ALTER TABLE customer_order ALTER COLUMN country_code SET NOT NULL;
ALTER TABLE customer_order ADD CONSTRAINT ck_order_country_code CHECK (country_code ~ '^[A-Z]{2}$');
ALTER TABLE customer_order ADD CONSTRAINT ck_order_fulfillment_status CHECK (fulfillment_status IN (
    'NOT_REQUESTED','DELIVERY_PENDING','DELIVERY_ASSIGNED','PICKED_UP','OUT_FOR_DELIVERY',
    'DELIVERED','DELIVERY_FAILED','DELIVERY_CANCEL_REQUESTED','DELIVERY_CANCELLED'));
CREATE UNIQUE INDEX uq_customer_order_delivery_id ON customer_order(delivery_id) WHERE delivery_id IS NOT NULL;
