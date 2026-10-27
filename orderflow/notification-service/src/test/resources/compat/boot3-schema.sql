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
CREATE TABLE public.notification (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    type character varying(64) NOT NULL,
    title character varying(120) NOT NULL,
    message character varying(500) NOT NULL,
    source_event_id uuid NOT NULL,
    source_aggregate_id uuid,
    read boolean DEFAULT false NOT NULL,
    read_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL
);
CREATE TABLE public.notification_delivery (
    id uuid NOT NULL,
    notification_id uuid NOT NULL,
    channel character varying(16) NOT NULL,
    destination character varying(254) NOT NULL,
    status character varying(16) NOT NULL,
    attempt_count integer DEFAULT 0 NOT NULL,
    last_failure_code character varying(80),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    sent_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_notification_channel CHECK (((channel)::text = ANY ((ARRAY['EMAIL'::character varying, 'SMS'::character varying])::text[]))),
    CONSTRAINT ck_notification_delivery_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying, 'SENT'::character varying, 'FAILED'::character varying, 'SKIPPED'::character varying])::text[])))
);
CREATE TABLE public.notification_preference (
    user_id uuid NOT NULL,
    email_enabled boolean DEFAULT false NOT NULL,
    sms_enabled boolean DEFAULT false NOT NULL,
    email_address character varying(254),
    phone_number character varying(32),
    updated_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_email_destination CHECK (((NOT email_enabled) OR (email_address IS NOT NULL))),
    CONSTRAINT ck_sms_destination CHECK (((NOT sms_enabled) OR (phone_number IS NOT NULL)))
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
    CONSTRAINT ck_notification_outbox_status CHECK (((status)::text = ANY ((ARRAY['NEW'::character varying, 'IN_PROGRESS'::character varying, 'PUBLISHED'::character varying, 'FAILED'::character varying])::text[])))
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
    CONSTRAINT ck_notification_job_status CHECK (((status)::text = ANY ((ARRAY['RUNNING'::character varying, 'SUCCEEDED'::character varying, 'PARTIAL'::character varying, 'FAILED'::character varying, 'SKIPPED_LOCKED'::character varying])::text[])))
);
ALTER TABLE ONLY public.outbox_event ALTER COLUMN event_sequence SET DEFAULT nextval('public.outbox_event_event_sequence_seq'::regclass);
INSERT INTO public.boot3_compatibility_probe VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '{"items": [{"currency": "USD", "quantity": 3, "lineTotal": 123456789.123456, "productId": "30000000-0000-0000-0000-000000000003", "unitPrice": 41152263.041152, "productName": "Precision item", "productVersion": 7}], "orderId": "10000000-0000-0000-0000-000000000001", "currency": "USD", "metadata": {"eventId": "9773d81a-a3f6-3633-95b3-9ac7a2337a3c", "producer": "contract-test", "eventType": "ORDER_CREATED", "occurredAt": "2026-01-02T03:04:05.123456Z", "aggregateId": "10000000-0000-0000-0000-000000000001", "causationId": null, "eventVersion": 1, "aggregateType": "ORDER", "correlationId": "contract-correlation"}, "customerId": "20000000-0000-0000-0000-000000000002", "totalAmount": 123456789.123456}', 123456789.123456, '2026-01-02 03:04:05.123456+00');
INSERT INTO public.flyway_schema_history VALUES (1, '1', 'create notification schema', 'SQL', 'V1__create_notification_schema.sql', -463158010, 'compat', '2026-10-27 07:00:39.497798', 19, true);
INSERT INTO public.flyway_schema_history VALUES (2, '2', 'add scheduler tables', 'SQL', 'V2__add_scheduler_tables.sql', -809440641, 'compat', '2026-10-27 07:00:39.523575', 10, true);
INSERT INTO public.flyway_schema_history VALUES (3, '3', 'preserve outbox aggregate order', 'SQL', 'V3__preserve_outbox_aggregate_order.sql', -1565373076, 'compat', '2026-10-27 07:00:39.539567', 9, true);
SELECT pg_catalog.setval('public.outbox_event_event_sequence_seq', 1, false);
ALTER TABLE ONLY public.boot3_compatibility_probe
    ADD CONSTRAINT boot3_compatibility_probe_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);
ALTER TABLE ONLY public.notification_delivery
    ADD CONSTRAINT notification_delivery_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.notification
    ADD CONSTRAINT notification_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.notification_preference
    ADD CONSTRAINT notification_preference_pkey PRIMARY KEY (user_id);
ALTER TABLE ONLY public.outbox_event
    ADD CONSTRAINT outbox_event_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.processed_event
    ADD CONSTRAINT processed_event_pkey PRIMARY KEY (event_id, consumer_name);
ALTER TABLE ONLY public.scheduled_job_lock
    ADD CONSTRAINT scheduled_job_lock_pkey PRIMARY KEY (job_name);
ALTER TABLE ONLY public.scheduled_job_run
    ADD CONSTRAINT scheduled_job_run_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.notification_delivery
    ADD CONSTRAINT uq_notification_channel UNIQUE (notification_id, channel);
ALTER TABLE ONLY public.notification
    ADD CONSTRAINT uq_notification_source UNIQUE (user_id, type, source_event_id);
CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);
CREATE INDEX idx_notification_delivery_status ON public.notification_delivery USING btree (status, created_at);
CREATE INDEX idx_notification_job_run ON public.scheduled_job_run USING btree (job_name, started_at DESC);
CREATE INDEX idx_notification_outbox_due ON public.outbox_event USING btree (status, available_at);
CREATE INDEX idx_notification_outbox_retention ON public.outbox_event USING btree (published_at) WHERE ((status)::text = 'PUBLISHED'::text);
CREATE INDEX idx_notification_processed_retention ON public.processed_event USING btree (processed_at);
CREATE INDEX idx_notification_unread ON public.notification USING btree (user_id, created_at DESC) WHERE (read = false);
CREATE INDEX idx_notification_user_created ON public.notification USING btree (user_id, created_at DESC);
CREATE INDEX idx_outbox_aggregate_sequence ON public.outbox_event USING btree (topic, message_key, event_sequence);
CREATE UNIQUE INDEX uq_outbox_event_sequence ON public.outbox_event USING btree (event_sequence);
ALTER TABLE ONLY public.notification_delivery
    ADD CONSTRAINT notification_delivery_notification_id_fkey FOREIGN KEY (notification_id) REFERENCES public.notification(id);

-- Representative business record for existing-data ORM/JDBC verification.
INSERT INTO public.notification(id,user_id,type,title,message,source_event_id,created_at) VALUES('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb','cccccccc-cccc-cccc-cccc-cccccccccccc','PAYMENT_SUCCEEDED','Synthetic title','Synthetic message','cccccccc-cccc-cccc-cccc-cccccccccccc','2026-01-02T03:04:05.123456Z');
