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
    CONSTRAINT ck_payment_idempotency_scope CHECK (((scope)::text = ANY ((ARRAY['CONFIRM_PAYMENT'::character varying, 'CANCEL_PAYMENT'::character varying, 'CREATE_REFUND'::character varying])::text[])))
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
    CONSTRAINT ck_payment_outbox_status CHECK (((status)::text = ANY ((ARRAY['NEW'::character varying, 'IN_PROGRESS'::character varying, 'PUBLISHED'::character varying, 'FAILED'::character varying])::text[])))
);
CREATE SEQUENCE public.outbox_event_event_sequence_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE public.outbox_event_event_sequence_seq OWNED BY public.outbox_event.event_sequence;
CREATE TABLE public.payment (
    id uuid NOT NULL,
    order_id uuid NOT NULL,
    customer_id uuid NOT NULL,
    amount numeric(19,2) NOT NULL,
    currency character varying(3) NOT NULL,
    status character varying(32) NOT NULL,
    provider character varying(32) NOT NULL,
    provider_payment_id character varying(160),
    provider_client_reference character varying(160),
    payment_deadline timestamp with time zone NOT NULL,
    failure_code character varying(80),
    failure_category character varying(48),
    refunded_amount numeric(19,2) DEFAULT 0 NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    succeeded_at timestamp with time zone,
    failed_at timestamp with time zone,
    cancelled_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_payment_amount_positive CHECK ((amount > (0)::numeric)),
    CONSTRAINT ck_payment_currency CHECK (((currency)::text ~ '^[A-Z]{3}$'::text)),
    CONSTRAINT ck_payment_refunded_amount CHECK (((refunded_amount >= (0)::numeric) AND (refunded_amount <= amount))),
    CONSTRAINT ck_payment_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'REQUIRES_ACTION'::character varying, 'PROCESSING'::character varying, 'SUCCEEDED'::character varying, 'FAILED'::character varying, 'CANCEL_REQUESTED'::character varying, 'CANCELLED'::character varying, 'REFUND_PENDING'::character varying, 'PARTIALLY_REFUNDED'::character varying, 'REFUNDED'::character varying])::text[])))
);
CREATE TABLE public.payment_attempt (
    id uuid NOT NULL,
    payment_id uuid NOT NULL,
    attempt_number integer NOT NULL,
    provider character varying(32) NOT NULL,
    provider_attempt_id character varying(160),
    status character varying(24) NOT NULL,
    failure_code character varying(80),
    failure_category character varying(48),
    retryable boolean,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    completed_at timestamp with time zone,
    correlation_id character varying(128),
    CONSTRAINT ck_payment_attempt_number CHECK ((attempt_number > 0)),
    CONSTRAINT ck_payment_attempt_status CHECK (((status)::text = ANY ((ARRAY['CREATED'::character varying, 'SUBMITTED'::character varying, 'PROCESSING'::character varying, 'SUCCEEDED'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying])::text[])))
);
CREATE TABLE public.payment_cancellation_tombstone (
    order_id uuid NOT NULL,
    customer_id uuid NOT NULL,
    payment_id uuid,
    reason_code character varying(80) NOT NULL,
    requested_at timestamp with time zone NOT NULL,
    correlation_id character varying(128) NOT NULL
);
CREATE TABLE public.processed_event (
    event_id uuid NOT NULL,
    event_type character varying(80) NOT NULL,
    aggregate_id uuid NOT NULL,
    consumer_name character varying(120) NOT NULL,
    processed_at timestamp with time zone NOT NULL,
    correlation_id character varying(128)
);
CREATE TABLE public.provider_webhook_event (
    id uuid NOT NULL,
    provider character varying(32) NOT NULL,
    provider_event_id character varying(160) NOT NULL,
    event_type character varying(80) NOT NULL,
    payload_hash character varying(64) NOT NULL,
    payment_id uuid,
    refund_id uuid,
    received_at timestamp with time zone NOT NULL,
    processed_at timestamp with time zone,
    processing_status character varying(24) NOT NULL,
    failure_code character varying(80),
    CONSTRAINT ck_webhook_processing_status CHECK (((processing_status)::text = ANY ((ARRAY['RECEIVED'::character varying, 'PROCESSED'::character varying, 'FAILED'::character varying])::text[])))
);
CREATE TABLE public.refund (
    id uuid NOT NULL,
    payment_id uuid NOT NULL,
    order_id uuid NOT NULL,
    customer_id uuid NOT NULL,
    amount numeric(19,2) NOT NULL,
    currency character varying(3) NOT NULL,
    status character varying(24) NOT NULL,
    reason_code character varying(80) NOT NULL,
    provider_refund_id character varying(160),
    failure_code character varying(80),
    created_by uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    succeeded_at timestamp with time zone,
    failed_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_refund_amount_positive CHECK ((amount > (0)::numeric)),
    CONSTRAINT ck_refund_currency CHECK (((currency)::text ~ '^[A-Z]{3}$'::text)),
    CONSTRAINT ck_refund_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying, 'SUCCEEDED'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying])::text[])))
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
    CONSTRAINT ck_payment_job_status CHECK (((status)::text = ANY ((ARRAY['RUNNING'::character varying, 'SUCCEEDED'::character varying, 'PARTIAL'::character varying, 'FAILED'::character varying, 'SKIPPED_LOCKED'::character varying])::text[])))
);
ALTER TABLE ONLY public.outbox_event ALTER COLUMN event_sequence SET DEFAULT nextval('public.outbox_event_event_sequence_seq'::regclass);
INSERT INTO public.boot3_compatibility_probe VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '{"items": [{"currency": "USD", "quantity": 3, "lineTotal": 123456789.123456, "productId": "30000000-0000-0000-0000-000000000003", "unitPrice": 41152263.041152, "productName": "Precision item", "productVersion": 7}], "orderId": "10000000-0000-0000-0000-000000000001", "currency": "USD", "metadata": {"eventId": "9773d81a-a3f6-3633-95b3-9ac7a2337a3c", "producer": "contract-test", "eventType": "ORDER_CREATED", "occurredAt": "2026-01-02T03:04:05.123456Z", "aggregateId": "10000000-0000-0000-0000-000000000001", "causationId": null, "eventVersion": 1, "aggregateType": "ORDER", "correlationId": "contract-correlation"}, "customerId": "20000000-0000-0000-0000-000000000002", "totalAmount": 123456789.123456}', 123456789.123456, '2026-01-02 03:04:05.123456+00');
INSERT INTO public.flyway_schema_history VALUES (1, '1', 'create payment schema', 'SQL', 'V1__create_payment_schema.sql', -635398860, 'compat', '2026-10-27 07:00:39.156698', 33, true);
INSERT INTO public.flyway_schema_history VALUES (2, '2', 'add scheduler tables', 'SQL', 'V2__add_scheduler_tables.sql', 1696287851, 'compat', '2026-10-27 07:00:39.197323', 9, true);
INSERT INTO public.flyway_schema_history VALUES (3, '3', 'preserve outbox aggregate order', 'SQL', 'V3__preserve_outbox_aggregate_order.sql', -1565373076, 'compat', '2026-10-27 07:00:39.212648', 12, true);
SELECT pg_catalog.setval('public.outbox_event_event_sequence_seq', 1, false);
ALTER TABLE ONLY public.boot3_compatibility_probe
    ADD CONSTRAINT boot3_compatibility_probe_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);
ALTER TABLE ONLY public.idempotency_record
    ADD CONSTRAINT idempotency_record_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.outbox_event
    ADD CONSTRAINT outbox_event_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.payment_attempt
    ADD CONSTRAINT payment_attempt_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.payment_cancellation_tombstone
    ADD CONSTRAINT payment_cancellation_tombstone_pkey PRIMARY KEY (order_id);
ALTER TABLE ONLY public.payment
    ADD CONSTRAINT payment_order_id_key UNIQUE (order_id);
ALTER TABLE ONLY public.payment
    ADD CONSTRAINT payment_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.payment
    ADD CONSTRAINT payment_provider_payment_id_key UNIQUE (provider_payment_id);
ALTER TABLE ONLY public.processed_event
    ADD CONSTRAINT processed_event_pkey PRIMARY KEY (event_id, consumer_name);
ALTER TABLE ONLY public.provider_webhook_event
    ADD CONSTRAINT provider_webhook_event_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.refund
    ADD CONSTRAINT refund_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.refund
    ADD CONSTRAINT refund_provider_refund_id_key UNIQUE (provider_refund_id);
ALTER TABLE ONLY public.scheduled_job_lock
    ADD CONSTRAINT scheduled_job_lock_pkey PRIMARY KEY (job_name);
ALTER TABLE ONLY public.scheduled_job_run
    ADD CONSTRAINT scheduled_job_run_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.payment_attempt
    ADD CONSTRAINT uq_payment_attempt_number UNIQUE (payment_id, attempt_number);
ALTER TABLE ONLY public.idempotency_record
    ADD CONSTRAINT uq_payment_idempotency UNIQUE (scope, actor_id, idempotency_key);
ALTER TABLE ONLY public.provider_webhook_event
    ADD CONSTRAINT uq_webhook_provider_event UNIQUE (provider, provider_event_id);
CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);
CREATE INDEX idx_outbox_aggregate_sequence ON public.outbox_event USING btree (topic, message_key, event_sequence);
CREATE INDEX idx_payment_attempt_payment ON public.payment_attempt USING btree (payment_id, attempt_number);
CREATE INDEX idx_payment_customer_created ON public.payment USING btree (customer_id, created_at DESC);
CREATE INDEX idx_payment_idempotency_retention ON public.idempotency_record USING btree (completed_at) WHERE (completed_at IS NOT NULL);
CREATE INDEX idx_payment_job_run ON public.scheduled_job_run USING btree (job_name, started_at DESC);
CREATE INDEX idx_payment_outbox_due ON public.outbox_event USING btree (status, available_at);
CREATE INDEX idx_payment_outbox_retention ON public.outbox_event USING btree (published_at) WHERE ((status)::text = 'PUBLISHED'::text);
CREATE INDEX idx_payment_processed_retention ON public.processed_event USING btree (processed_at);
CREATE INDEX idx_payment_status_created ON public.payment USING btree (status, created_at DESC);
CREATE INDEX idx_refund_payment_created ON public.refund USING btree (payment_id, created_at DESC);
CREATE UNIQUE INDEX uq_outbox_event_sequence ON public.outbox_event USING btree (event_sequence);
ALTER TABLE ONLY public.payment_attempt
    ADD CONSTRAINT payment_attempt_payment_id_fkey FOREIGN KEY (payment_id) REFERENCES public.payment(id);
ALTER TABLE ONLY public.refund
    ADD CONSTRAINT refund_payment_id_fkey FOREIGN KEY (payment_id) REFERENCES public.payment(id);

-- Representative business record for existing-data ORM/JDBC verification.
INSERT INTO public.payment(id,order_id,customer_id,amount,currency,status,provider,payment_deadline,created_at,updated_at,version) VALUES('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb','cccccccc-cccc-cccc-cccc-cccccccccccc','cccccccc-cccc-cccc-cccc-cccccccccccc',123.45,'USD','FAILED','SIMULATOR','2026-01-02T03:04:05.123456Z','2026-01-02T03:04:05.123456Z','2026-01-02T03:04:05.123456Z',7);
