-- Synthetic Boot 3.5.16 / Flyway 11 schema and data, generated before migration.
SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;
SET default_tablespace = '';
SET default_table_access_method = heap;
CREATE TABLE public.boot3_compatibility_probe (
    id uuid NOT NULL,
    payload jsonb NOT NULL,
    amount numeric(19,6) NOT NULL,
    occurred_at timestamp with time zone NOT NULL
);
CREATE TABLE public.customer_order (
    id uuid NOT NULL,
    customer_id uuid NOT NULL,
    status character varying(32) NOT NULL,
    currency character varying(3) NOT NULL,
    subtotal numeric(19,2) NOT NULL,
    total_amount numeric(19,2) NOT NULL,
    cart_version bigint NOT NULL,
    cancellation_reason character varying(200),
    inventory_reservation_id uuid,
    inventory_expires_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    payment_id uuid,
    paid_at timestamp with time zone,
    payment_failed_at timestamp with time zone,
    payment_failure_code character varying(80),
    refunded_amount numeric(19,2) DEFAULT 0 NOT NULL,
    refunded_at timestamp with time zone,
    inventory_released_at timestamp with time zone,
    fulfillment_status character varying(40) DEFAULT 'NOT_REQUESTED'::character varying NOT NULL,
    recipient_name character varying(120) NOT NULL,
    address_line_1 character varying(200) NOT NULL,
    address_line_2 character varying(200),
    city character varying(120) NOT NULL,
    state_or_region character varying(120) NOT NULL,
    postal_code character varying(32) NOT NULL,
    country_code character varying(2) NOT NULL,
    delivery_instructions character varying(300),
    delivery_id uuid,
    delivery_agent_id uuid,
    delivery_requested_at timestamp with time zone,
    delivery_assigned_at timestamp with time zone,
    picked_up_at timestamp with time zone,
    out_for_delivery_at timestamp with time zone,
    delivered_at timestamp with time zone,
    delivery_failed_at timestamp with time zone,
    delivery_cancelled_at timestamp with time zone,
    CONSTRAINT ck_order_country_code CHECK (((country_code)::text ~ '^[A-Z]{2}$'::text)),
    CONSTRAINT ck_order_currency CHECK (((currency)::text ~ '^[A-Z]{3}$'::text)),
    CONSTRAINT ck_order_fulfillment_status CHECK (((fulfillment_status)::text = ANY ((ARRAY['NOT_REQUESTED'::character varying, 'DELIVERY_PENDING'::character varying, 'DELIVERY_ASSIGNED'::character varying, 'PICKED_UP'::character varying, 'OUT_FOR_DELIVERY'::character varying, 'DELIVERED'::character varying, 'DELIVERY_FAILED'::character varying, 'DELIVERY_CANCEL_REQUESTED'::character varying, 'DELIVERY_CANCELLED'::character varying])::text[]))),
    CONSTRAINT ck_order_refunded_amount CHECK (((refunded_amount >= (0)::numeric) AND (refunded_amount <= total_amount))),
    CONSTRAINT ck_order_status CHECK (((status)::text = ANY ((ARRAY['INVENTORY_PENDING'::character varying, 'INVENTORY_RESERVED'::character varying, 'INVENTORY_REJECTED'::character varying, 'PAYMENT_PENDING'::character varying, 'PAID'::character varying, 'PAYMENT_FAILED'::character varying, 'CANCEL_REQUESTED'::character varying, 'CANCELLED'::character varying, 'PARTIALLY_REFUNDED'::character varying, 'REFUNDED'::character varying])::text[]))),
    CONSTRAINT ck_order_subtotal_non_negative CHECK ((subtotal >= (0)::numeric)),
    CONSTRAINT ck_order_total_non_negative CHECK ((total_amount >= (0)::numeric))
);
CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);
CREATE TABLE public.idempotency_record (
    id uuid NOT NULL,
    scope character varying(32) NOT NULL,
    actor_id uuid NOT NULL,
    idempotency_key character varying(128) NOT NULL,
    request_hash character varying(64) NOT NULL,
    resource_id uuid,
    response_status integer,
    created_at timestamp with time zone NOT NULL,
    completed_at timestamp with time zone,
    CONSTRAINT ck_idempotency_scope CHECK (((scope)::text = ANY ((ARRAY['CREATE_ORDER'::character varying, 'CANCEL_ORDER'::character varying])::text[])))
);
CREATE TABLE public.order_item (
    id uuid NOT NULL,
    order_id uuid NOT NULL,
    product_id uuid NOT NULL,
    product_name character varying(200) NOT NULL,
    quantity bigint NOT NULL,
    unit_price numeric(19,2) NOT NULL,
    line_total numeric(19,2) NOT NULL,
    currency character varying(3) NOT NULL,
    product_version bigint,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_order_item_price_non_negative CHECK ((unit_price >= (0)::numeric)),
    CONSTRAINT ck_order_item_quantity_positive CHECK ((quantity > 0)),
    CONSTRAINT ck_order_item_total_non_negative CHECK ((line_total >= (0)::numeric))
);
CREATE TABLE public.order_status_history (
    id uuid NOT NULL,
    order_id uuid NOT NULL,
    previous_status character varying(32),
    new_status character varying(32) NOT NULL,
    reason_code character varying(120),
    event_id uuid,
    correlation_id character varying(128),
    changed_at timestamp with time zone NOT NULL
);
CREATE TABLE public.outbox_event (
    id uuid NOT NULL,
    aggregate_type character varying(64) NOT NULL,
    aggregate_id uuid NOT NULL,
    event_type character varying(80) NOT NULL,
    event_version integer NOT NULL,
    topic character varying(255) NOT NULL,
    message_key character varying(128) NOT NULL,
    payload jsonb NOT NULL,
    correlation_id character varying(128),
    causation_id uuid,
    status character varying(16) NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    available_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    published_at timestamp with time zone,
    last_error_code character varying(200),
    locked_at timestamp with time zone,
    locked_by character varying(128),
    event_sequence bigint NOT NULL,
    CONSTRAINT ck_order_outbox_status CHECK (((status)::text = ANY ((ARRAY['NEW'::character varying, 'IN_PROGRESS'::character varying, 'PUBLISHED'::character varying, 'FAILED'::character varying])::text[])))
);
CREATE SEQUENCE public.outbox_event_event_sequence_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE public.outbox_event_event_sequence_seq OWNED BY public.outbox_event.event_sequence;
CREATE TABLE public.processed_event (
    event_id uuid NOT NULL,
    event_type character varying(80) NOT NULL,
    aggregate_id uuid NOT NULL,
    consumer_name character varying(120) NOT NULL,
    processed_at timestamp with time zone NOT NULL,
    correlation_id character varying(128)
);
CREATE TABLE public.scheduled_job_lock (
    job_name character varying(120) NOT NULL,
    locked_until timestamp with time zone NOT NULL,
    locked_at timestamp with time zone,
    locked_by character varying(128),
    last_started_at timestamp with time zone,
    last_completed_at timestamp with time zone,
    last_status character varying(24),
    last_duration_ms bigint,
    failure_count bigint DEFAULT 0 NOT NULL,
    last_error_code character varying(80),
    updated_at timestamp with time zone NOT NULL
);
CREATE TABLE public.scheduled_job_run (
    id uuid NOT NULL,
    job_name character varying(120) NOT NULL,
    instance_id character varying(128) NOT NULL,
    started_at timestamp with time zone NOT NULL,
    completed_at timestamp with time zone,
    status character varying(24) NOT NULL,
    processed_count bigint DEFAULT 0 NOT NULL,
    failure_count bigint DEFAULT 0 NOT NULL,
    duration_ms bigint,
    error_code character varying(80),
    correlation_id character varying(128),
    CONSTRAINT ck_order_job_status CHECK (((status)::text = ANY ((ARRAY['RUNNING'::character varying, 'SUCCEEDED'::character varying, 'PARTIAL'::character varying, 'FAILED'::character varying, 'SKIPPED_LOCKED'::character varying])::text[])))
);
ALTER TABLE ONLY public.outbox_event ALTER COLUMN event_sequence SET DEFAULT nextval('public.outbox_event_event_sequence_seq'::regclass);
INSERT INTO public.boot3_compatibility_probe VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '{"items": [{"currency": "USD", "quantity": 3, "lineTotal": 123456789.123456, "productId": "30000000-0000-0000-0000-000000000003", "unitPrice": 41152263.041152, "productName": "Precision item", "productVersion": 7}], "orderId": "10000000-0000-0000-0000-000000000001", "currency": "USD", "metadata": {"eventId": "9773d81a-a3f6-3633-95b3-9ac7a2337a3c", "producer": "contract-test", "eventType": "ORDER_CREATED", "occurredAt": "2026-01-02T03:04:05.123456Z", "aggregateId": "10000000-0000-0000-0000-000000000001", "causationId": null, "eventVersion": 1, "aggregateType": "ORDER", "correlationId": "contract-correlation"}, "customerId": "20000000-0000-0000-0000-000000000002", "totalAmount": 123456789.123456}', 123456789.123456, '2026-01-02 03:04:05.123456+00');
INSERT INTO public.flyway_schema_history VALUES (1, '1', 'create order schema', 'SQL', 'V1__create_order_schema.sql', -290063591, 'compat', '2026-10-27 07:00:38.969379', 24, true);
INSERT INTO public.flyway_schema_history VALUES (2, '2', 'add payment lifecycle', 'SQL', 'V2__add_payment_lifecycle.sql', 1453130813, 'compat', '2026-10-27 07:00:39.003457', 5, true);
INSERT INTO public.flyway_schema_history VALUES (3, '3', 'add shipping and fulfillment', 'SQL', 'V3__add_shipping_and_fulfillment.sql', 1789776398, 'compat', '2026-10-27 07:00:39.014396', 10, true);
INSERT INTO public.flyway_schema_history VALUES (4, '4', 'add scheduler tables', 'SQL', 'V4__add_scheduler_tables.sql', -1249777963, 'compat', '2026-10-27 07:00:39.032363', 9, true);
INSERT INTO public.flyway_schema_history VALUES (5, '5', 'preserve outbox aggregate order', 'SQL', 'V5__preserve_outbox_aggregate_order.sql', -1565373076, 'compat', '2026-10-27 07:00:39.047944', 10, true);
SELECT pg_catalog.setval('public.outbox_event_event_sequence_seq', 1, false);
ALTER TABLE ONLY public.boot3_compatibility_probe
    ADD CONSTRAINT boot3_compatibility_probe_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.customer_order
    ADD CONSTRAINT customer_order_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);
ALTER TABLE ONLY public.idempotency_record
    ADD CONSTRAINT idempotency_record_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.order_item
    ADD CONSTRAINT order_item_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.order_status_history
    ADD CONSTRAINT order_status_history_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.outbox_event
    ADD CONSTRAINT outbox_event_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.processed_event
    ADD CONSTRAINT processed_event_pkey PRIMARY KEY (event_id, consumer_name);
ALTER TABLE ONLY public.scheduled_job_lock
    ADD CONSTRAINT scheduled_job_lock_pkey PRIMARY KEY (job_name);
ALTER TABLE ONLY public.scheduled_job_run
    ADD CONSTRAINT scheduled_job_run_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.idempotency_record
    ADD CONSTRAINT uq_idempotency_scope_actor_key UNIQUE (scope, actor_id, idempotency_key);
ALTER TABLE ONLY public.order_item
    ADD CONSTRAINT uq_order_item_product UNIQUE (order_id, product_id);
CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);
CREATE INDEX idx_customer_order_customer_created ON public.customer_order USING btree (customer_id, created_at DESC);
CREATE INDEX idx_customer_order_payment_id ON public.customer_order USING btree (payment_id) WHERE (payment_id IS NOT NULL);
CREATE INDEX idx_customer_order_status_created ON public.customer_order USING btree (status, created_at DESC);
CREATE INDEX idx_order_history_order_changed ON public.order_status_history USING btree (order_id, changed_at, id);
CREATE INDEX idx_order_idempotency_retention ON public.idempotency_record USING btree (completed_at) WHERE (completed_at IS NOT NULL);
CREATE INDEX idx_order_job_run ON public.scheduled_job_run USING btree (job_name, started_at DESC);
CREATE INDEX idx_order_outbox_due ON public.outbox_event USING btree (status, available_at);
CREATE INDEX idx_order_outbox_retention ON public.outbox_event USING btree (published_at) WHERE ((status)::text = 'PUBLISHED'::text);
CREATE INDEX idx_order_processed_retention ON public.processed_event USING btree (processed_at);
CREATE INDEX idx_outbox_aggregate_sequence ON public.outbox_event USING btree (topic, message_key, event_sequence);
CREATE UNIQUE INDEX uq_customer_order_delivery_id ON public.customer_order USING btree (delivery_id) WHERE (delivery_id IS NOT NULL);
CREATE UNIQUE INDEX uq_outbox_event_sequence ON public.outbox_event USING btree (event_sequence);
ALTER TABLE ONLY public.order_item
    ADD CONSTRAINT order_item_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.customer_order(id);
ALTER TABLE ONLY public.order_status_history
    ADD CONSTRAINT order_status_history_order_id_fkey FOREIGN KEY (order_id) REFERENCES public.customer_order(id);

-- Representative business record for existing-data ORM/JDBC verification.
INSERT INTO public.customer_order(id,customer_id,status,currency,subtotal,total_amount,cart_version,created_at,updated_at,version,recipient_name,address_line_1,city,state_or_region,postal_code,country_code) VALUES('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb','cccccccc-cccc-cccc-cccc-cccccccccccc','CANCELLED','USD',123.45,123.45,7,'2026-01-02T03:04:05.123456Z','2026-01-02T03:04:05.123456Z',7,'Synthetic recipient','Test street','Test city','Test region','12345','US');
