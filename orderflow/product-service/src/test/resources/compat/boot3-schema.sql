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
CREATE TABLE public.categories (
    id uuid NOT NULL,
    name character varying(120) NOT NULL,
    normalized_name character varying(120) NOT NULL,
    slug character varying(120) NOT NULL,
    description character varying(1000),
    status character varying(16) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_categories_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
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
CREATE TABLE public.products (
    id uuid NOT NULL,
    vendor_id uuid NOT NULL,
    sku character varying(64) NOT NULL,
    slug character varying(120) NOT NULL,
    name character varying(200) NOT NULL,
    description character varying(4000),
    price_amount numeric(19,2) NOT NULL,
    currency character varying(3) NOT NULL,
    status character varying(16) NOT NULL,
    category_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_products_currency CHECK (((currency)::text ~ '^[A-Z]{3}$'::text)),
    CONSTRAINT ck_products_price_non_negative CHECK ((price_amount >= (0)::numeric)),
    CONSTRAINT ck_products_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'ACTIVE'::character varying, 'INACTIVE'::character varying, 'ARCHIVED'::character varying])::text[])))
);
INSERT INTO public.boot3_compatibility_probe VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '{"items": [{"currency": "USD", "quantity": 3, "lineTotal": 123456789.123456, "productId": "30000000-0000-0000-0000-000000000003", "unitPrice": 41152263.041152, "productName": "Precision item", "productVersion": 7}], "orderId": "10000000-0000-0000-0000-000000000001", "currency": "USD", "metadata": {"eventId": "9773d81a-a3f6-3633-95b3-9ac7a2337a3c", "producer": "contract-test", "eventType": "ORDER_CREATED", "occurredAt": "2026-01-02T03:04:05.123456Z", "aggregateId": "10000000-0000-0000-0000-000000000001", "causationId": null, "eventVersion": 1, "aggregateType": "ORDER", "correlationId": "contract-correlation"}, "customerId": "20000000-0000-0000-0000-000000000002", "totalAmount": 123456789.123456}', 123456789.123456, '2026-01-02 03:04:05.123456+00');
INSERT INTO public.categories VALUES ('10000000-0000-0000-0000-000000000001', 'Electronics', 'electronics', 'electronics', 'Electronic devices and accessories', 'ACTIVE', '2026-10-27 01:30:38.57569+00', '2026-10-27 01:30:38.57569+00', 0);
INSERT INTO public.categories VALUES ('10000000-0000-0000-0000-000000000002', 'Books', 'books', 'books', 'Printed and digital reading products', 'ACTIVE', '2026-10-27 01:30:38.57569+00', '2026-10-27 01:30:38.57569+00', 0);
INSERT INTO public.categories VALUES ('10000000-0000-0000-0000-000000000003', 'Home and Kitchen', 'home and kitchen', 'home-and-kitchen', 'Home and kitchen products', 'ACTIVE', '2026-10-27 01:30:38.57569+00', '2026-10-27 01:30:38.57569+00', 0);
INSERT INTO public.categories VALUES ('10000000-0000-0000-0000-000000000004', 'Clothing', 'clothing', 'clothing', 'Clothing and apparel', 'ACTIVE', '2026-10-27 01:30:38.57569+00', '2026-10-27 01:30:38.57569+00', 0);
INSERT INTO public.flyway_schema_history VALUES (1, '1', 'create product catalog', 'SQL', 'V1__create_product_catalog.sql', -276763761, 'compat', '2026-10-27 07:00:38.570671', 27, true);
ALTER TABLE ONLY public.boot3_compatibility_probe
    ADD CONSTRAINT boot3_compatibility_probe_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.categories
    ADD CONSTRAINT categories_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);
ALTER TABLE ONLY public.products
    ADD CONSTRAINT products_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.categories
    ADD CONSTRAINT uq_categories_normalized_name UNIQUE (normalized_name);
ALTER TABLE ONLY public.categories
    ADD CONSTRAINT uq_categories_slug UNIQUE (slug);
ALTER TABLE ONLY public.products
    ADD CONSTRAINT uq_products_slug UNIQUE (slug);
ALTER TABLE ONLY public.products
    ADD CONSTRAINT uq_products_vendor_sku UNIQUE (vendor_id, sku);
CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);
CREATE INDEX idx_products_price_currency ON public.products USING btree (currency, price_amount);
CREATE INDEX idx_products_public_catalog ON public.products USING btree (status, category_id, created_at DESC);
CREATE INDEX idx_products_vendor ON public.products USING btree (vendor_id, created_at DESC);
ALTER TABLE ONLY public.products
    ADD CONSTRAINT products_category_id_fkey FOREIGN KEY (category_id) REFERENCES public.categories(id);
