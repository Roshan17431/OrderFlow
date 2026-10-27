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
CREATE TABLE public.inventory_adjustment (
    id uuid NOT NULL,
    inventory_item_id uuid NOT NULL,
    product_id uuid NOT NULL,
    quantity_delta bigint NOT NULL,
    resulting_on_hand bigint NOT NULL,
    resulting_reserved bigint NOT NULL,
    reason character varying(120) NOT NULL,
    reference character varying(200),
    actor_user_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    correlation_id character varying(128),
    CONSTRAINT ck_inventory_adjustment_non_zero CHECK ((quantity_delta <> 0))
);
CREATE TABLE public.inventory_item (
    id uuid NOT NULL,
    product_id uuid NOT NULL,
    on_hand_quantity bigint NOT NULL,
    reserved_quantity bigint NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_inventory_on_hand_non_negative CHECK ((on_hand_quantity >= 0)),
    CONSTRAINT ck_inventory_reserved_non_negative CHECK ((reserved_quantity >= 0)),
    CONSTRAINT ck_inventory_reserved_within_on_hand CHECK ((reserved_quantity <= on_hand_quantity))
);
CREATE TABLE public.inventory_reservation (
    id uuid NOT NULL,
    order_id uuid NOT NULL,
    status character varying(16) NOT NULL,
    expires_at timestamp with time zone,
    failure_reason character varying(500),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    released_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_inventory_reservation_status CHECK (((status)::text = ANY ((ARRAY['RESERVED'::character varying, 'COMMITTED'::character varying, 'FULFILLED'::character varying, 'REJECTED'::character varying, 'RELEASED'::character varying, 'EXPIRED'::character varying])::text[])))
);
CREATE TABLE public.inventory_reservation_item (
    id uuid NOT NULL,
    reservation_id uuid NOT NULL,
    product_id uuid NOT NULL,
    quantity bigint NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_inventory_reservation_quantity_positive CHECK ((quantity > 0))
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
    CONSTRAINT ck_inventory_outbox_status CHECK (((status)::text = ANY ((ARRAY['NEW'::character varying, 'IN_PROGRESS'::character varying, 'PUBLISHED'::character varying, 'FAILED'::character varying])::text[])))
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
    CONSTRAINT ck_inventory_job_status CHECK (((status)::text = ANY ((ARRAY['RUNNING'::character varying, 'SUCCEEDED'::character varying, 'PARTIAL'::character varying, 'FAILED'::character varying, 'SKIPPED_LOCKED'::character varying])::text[])))
);
ALTER TABLE ONLY public.outbox_event ALTER COLUMN event_sequence SET DEFAULT nextval('public.outbox_event_event_sequence_seq'::regclass);
INSERT INTO public.boot3_compatibility_probe VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '{"items": [{"currency": "USD", "quantity": 3, "lineTotal": 123456789.123456, "productId": "30000000-0000-0000-0000-000000000003", "unitPrice": 41152263.041152, "productName": "Precision item", "productVersion": 7}], "orderId": "10000000-0000-0000-0000-000000000001", "currency": "USD", "metadata": {"eventId": "9773d81a-a3f6-3633-95b3-9ac7a2337a3c", "producer": "contract-test", "eventType": "ORDER_CREATED", "occurredAt": "2026-01-02T03:04:05.123456Z", "aggregateId": "10000000-0000-0000-0000-000000000001", "causationId": null, "eventVersion": 1, "aggregateType": "ORDER", "correlationId": "contract-correlation"}, "customerId": "20000000-0000-0000-0000-000000000002", "totalAmount": 123456789.123456}', 123456789.123456, '2026-01-02 03:04:05.123456+00');
INSERT INTO public.flyway_schema_history VALUES (1, '1', 'create inventory schema', 'SQL', 'V1__create_inventory_schema.sql', 1516609198, 'compat', '2026-10-27 07:00:38.730261', 60, true);
INSERT INTO public.flyway_schema_history VALUES (2, '2', 'add scheduler tables', 'SQL', 'V2__add_scheduler_tables.sql', 1305014289, 'compat', '2026-10-27 07:00:38.806322', 18, true);
INSERT INTO public.flyway_schema_history VALUES (3, '3', 'add committed inventory states', 'SQL', 'V3__add_committed_inventory_states.sql', 1538627820, 'compat', '2026-10-27 07:00:38.832164', 3, true);
INSERT INTO public.flyway_schema_history VALUES (4, '4', 'preserve outbox aggregate order', 'SQL', 'V4__preserve_outbox_aggregate_order.sql', -1565373076, 'compat', '2026-10-27 07:00:38.842376', 11, true);
SELECT pg_catalog.setval('public.outbox_event_event_sequence_seq', 1, false);
ALTER TABLE ONLY public.boot3_compatibility_probe
    ADD CONSTRAINT boot3_compatibility_probe_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);
ALTER TABLE ONLY public.inventory_adjustment
    ADD CONSTRAINT inventory_adjustment_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.inventory_item
    ADD CONSTRAINT inventory_item_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.inventory_item
    ADD CONSTRAINT inventory_item_product_id_key UNIQUE (product_id);
ALTER TABLE ONLY public.inventory_reservation_item
    ADD CONSTRAINT inventory_reservation_item_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.inventory_reservation
    ADD CONSTRAINT inventory_reservation_order_id_key UNIQUE (order_id);
ALTER TABLE ONLY public.inventory_reservation
    ADD CONSTRAINT inventory_reservation_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.outbox_event
    ADD CONSTRAINT outbox_event_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.processed_event
    ADD CONSTRAINT processed_event_pkey PRIMARY KEY (event_id, consumer_name);
ALTER TABLE ONLY public.scheduled_job_lock
    ADD CONSTRAINT scheduled_job_lock_pkey PRIMARY KEY (job_name);
ALTER TABLE ONLY public.scheduled_job_run
    ADD CONSTRAINT scheduled_job_run_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.inventory_reservation_item
    ADD CONSTRAINT uq_inventory_reservation_product UNIQUE (reservation_id, product_id);
CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);
CREATE INDEX idx_inventory_adjustment_product_created ON public.inventory_adjustment USING btree (product_id, created_at DESC);
CREATE INDEX idx_inventory_job_run ON public.scheduled_job_run USING btree (job_name, started_at DESC);
CREATE INDEX idx_inventory_outbox_due ON public.outbox_event USING btree (status, available_at);
CREATE INDEX idx_inventory_outbox_retention ON public.outbox_event USING btree (published_at) WHERE ((status)::text = 'PUBLISHED'::text);
CREATE INDEX idx_inventory_processed_retention ON public.processed_event USING btree (processed_at);
CREATE INDEX idx_inventory_reservation_expiration ON public.inventory_reservation USING btree (status, expires_at);
CREATE INDEX idx_outbox_aggregate_sequence ON public.outbox_event USING btree (topic, message_key, event_sequence);
CREATE UNIQUE INDEX uq_outbox_event_sequence ON public.outbox_event USING btree (event_sequence);
ALTER TABLE ONLY public.inventory_adjustment
    ADD CONSTRAINT inventory_adjustment_inventory_item_id_fkey FOREIGN KEY (inventory_item_id) REFERENCES public.inventory_item(id);
ALTER TABLE ONLY public.inventory_reservation_item
    ADD CONSTRAINT inventory_reservation_item_reservation_id_fkey FOREIGN KEY (reservation_id) REFERENCES public.inventory_reservation(id);

-- Representative business record for existing-data ORM/JDBC verification.
INSERT INTO public.inventory_item(id,product_id,on_hand_quantity,reserved_quantity,created_at,updated_at,version) VALUES('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb','cccccccc-cccc-cccc-cccc-cccccccccccc',20,3,'2026-01-02T03:04:05.123456Z','2026-01-02T03:04:05.123456Z',7);
