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
CREATE TABLE public.daily_operational_metric (
    id uuid NOT NULL,
    metric_date date NOT NULL,
    metric_name character varying(80) NOT NULL,
    dimension character varying(120) DEFAULT 'all'::character varying NOT NULL,
    count_value bigint DEFAULT 0 NOT NULL,
    decimal_value numeric(19,2),
    updated_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_daily_metric_count CHECK ((count_value >= 0))
);
CREATE TABLE public.daily_report (
    id uuid NOT NULL,
    report_date date NOT NULL,
    status character varying(16) NOT NULL,
    generated_at timestamp with time zone NOT NULL,
    finalized_at timestamp with time zone,
    orders_created bigint DEFAULT 0 NOT NULL,
    payments_succeeded bigint DEFAULT 0 NOT NULL,
    payments_failed bigint DEFAULT 0 NOT NULL,
    payment_volume numeric(19,2) DEFAULT 0 NOT NULL,
    refund_volume numeric(19,2) DEFAULT 0 NOT NULL,
    inventory_reservation_failures bigint DEFAULT 0 NOT NULL,
    deliveries_completed bigint DEFAULT 0 NOT NULL,
    deliveries_failed bigint DEFAULT 0 NOT NULL,
    notifications_delivered bigint DEFAULT 0 NOT NULL,
    notifications_failed bigint DEFAULT 0 NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_report_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'FINALIZED'::character varying])::text[])))
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
CREATE TABLE public.operational_incident (
    id uuid NOT NULL,
    incident_type character varying(80) NOT NULL,
    severity character varying(16) NOT NULL,
    source_service character varying(64) NOT NULL,
    aggregate_type character varying(64) NOT NULL,
    aggregate_id uuid,
    reason_code character varying(80),
    correlation_id character varying(128),
    occurred_at timestamp with time zone NOT NULL,
    resolved boolean DEFAULT false NOT NULL,
    resolved_at timestamp with time zone,
    resolution_note character varying(300),
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_incident_severity CHECK (((severity)::text = ANY ((ARRAY['INFO'::character varying, 'WARNING'::character varying, 'CRITICAL'::character varying])::text[])))
);
CREATE TABLE public.processed_event (
    event_id uuid NOT NULL,
    consumer_name character varying(120) NOT NULL,
    event_type character varying(80) NOT NULL,
    aggregate_id uuid NOT NULL,
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
    CONSTRAINT ck_job_run_status CHECK (((status)::text = ANY ((ARRAY['RUNNING'::character varying, 'SUCCEEDED'::character varying, 'PARTIAL'::character varying, 'FAILED'::character varying, 'SKIPPED_LOCKED'::character varying])::text[])))
);
INSERT INTO public.boot3_compatibility_probe VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '{"items": [{"currency": "USD", "quantity": 3, "lineTotal": 123456789.123456, "productId": "30000000-0000-0000-0000-000000000003", "unitPrice": 41152263.041152, "productName": "Precision item", "productVersion": 7}], "orderId": "10000000-0000-0000-0000-000000000001", "currency": "USD", "metadata": {"eventId": "9773d81a-a3f6-3633-95b3-9ac7a2337a3c", "producer": "contract-test", "eventType": "ORDER_CREATED", "occurredAt": "2026-01-02T03:04:05.123456Z", "aggregateId": "10000000-0000-0000-0000-000000000001", "causationId": null, "eventVersion": 1, "aggregateType": "ORDER", "correlationId": "contract-correlation"}, "customerId": "20000000-0000-0000-0000-000000000002", "totalAmount": 123456789.123456}', 123456789.123456, '2026-01-02 03:04:05.123456+00');
INSERT INTO public.flyway_schema_history VALUES (1, '1', 'create operations schema', 'SQL', 'V1__create_operations_schema.sql', -393505753, 'compat', '2026-10-27 07:00:39.653072', 25, true);
ALTER TABLE ONLY public.boot3_compatibility_probe
    ADD CONSTRAINT boot3_compatibility_probe_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.daily_operational_metric
    ADD CONSTRAINT daily_operational_metric_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.daily_report
    ADD CONSTRAINT daily_report_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.daily_report
    ADD CONSTRAINT daily_report_report_date_key UNIQUE (report_date);
ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);
ALTER TABLE ONLY public.operational_incident
    ADD CONSTRAINT operational_incident_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.processed_event
    ADD CONSTRAINT processed_event_pkey PRIMARY KEY (event_id, consumer_name);
ALTER TABLE ONLY public.scheduled_job_lock
    ADD CONSTRAINT scheduled_job_lock_pkey PRIMARY KEY (job_name);
ALTER TABLE ONLY public.scheduled_job_run
    ADD CONSTRAINT scheduled_job_run_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.daily_operational_metric
    ADD CONSTRAINT uq_daily_metric UNIQUE (metric_date, metric_name, dimension);
CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);
CREATE INDEX idx_daily_metric_date ON public.daily_operational_metric USING btree (metric_date, metric_name);
CREATE INDEX idx_daily_report_date ON public.daily_report USING btree (report_date DESC);
CREATE INDEX idx_incident_query ON public.operational_incident USING btree (resolved, severity, occurred_at DESC);
CREATE INDEX idx_job_run_history ON public.scheduled_job_run USING btree (job_name, started_at DESC);
CREATE INDEX idx_operations_processed_at ON public.processed_event USING btree (processed_at);

-- Representative business record for existing-data ORM/JDBC verification.
INSERT INTO public.daily_report(id,report_date,status,generated_at,payment_volume,refund_volume,version) VALUES('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb','2026-01-01','FINALIZED','2026-01-02T03:04:05.123456Z',123.45,3.45,7);
