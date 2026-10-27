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
CREATE TABLE public.oauth_accounts (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    provider character varying(20) NOT NULL,
    provider_user_id character varying(255) NOT NULL,
    email character varying(320) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_oauth_email_normalized CHECK (((email)::text = lower(btrim((email)::text)))),
    CONSTRAINT ck_oauth_provider CHECK (((provider)::text = ANY ((ARRAY['GOOGLE'::character varying, 'GITHUB'::character varying])::text[])))
);
CREATE TABLE public.platform_metadata (
    id uuid NOT NULL,
    service_name character varying(100) NOT NULL,
    schema_version character varying(32) NOT NULL,
    created_at timestamp with time zone NOT NULL
);
CREATE TABLE public.user_roles (
    user_id uuid NOT NULL,
    role character varying(32) NOT NULL,
    CONSTRAINT ck_user_roles_role CHECK (((role)::text = ANY ((ARRAY['CUSTOMER'::character varying, 'VENDOR'::character varying, 'DELIVERY_AGENT'::character varying, 'ADMIN'::character varying])::text[])))
);
CREATE TABLE public.users (
    id uuid NOT NULL,
    email character varying(320) NOT NULL,
    password_hash character varying(100),
    display_name character varying(100) NOT NULL,
    status character varying(20) NOT NULL,
    email_verified boolean DEFAULT false NOT NULL,
    token_version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_users_email_normalized CHECK (((email)::text = lower(btrim((email)::text)))),
    CONSTRAINT ck_users_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'LOCKED'::character varying, 'DISABLED'::character varying])::text[]))),
    CONSTRAINT ck_users_token_version CHECK ((token_version >= 0))
);
INSERT INTO public.boot3_compatibility_probe VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '{"items": [{"currency": "USD", "quantity": 3, "lineTotal": 123456789.123456, "productId": "30000000-0000-0000-0000-000000000003", "unitPrice": 41152263.041152, "productName": "Precision item", "productVersion": 7}], "orderId": "10000000-0000-0000-0000-000000000001", "currency": "USD", "metadata": {"eventId": "9773d81a-a3f6-3633-95b3-9ac7a2337a3c", "producer": "contract-test", "eventType": "ORDER_CREATED", "occurredAt": "2026-01-02T03:04:05.123456Z", "aggregateId": "10000000-0000-0000-0000-000000000001", "causationId": null, "eventVersion": 1, "aggregateType": "ORDER", "correlationId": "contract-correlation"}, "customerId": "20000000-0000-0000-0000-000000000002", "totalAmount": 123456789.123456}', 123456789.123456, '2026-01-02 03:04:05.123456+00');
INSERT INTO public.flyway_schema_history VALUES (1, '1', 'create platform metadata', 'SQL', 'V1__create_platform_metadata.sql', -1575418999, 'compat', '2026-10-27 07:00:38.242525', 11, true);
INSERT INTO public.flyway_schema_history VALUES (2, '2', 'create identity schema', 'SQL', 'V2__create_identity_schema.sql', -227602077, 'compat', '2026-10-27 07:00:38.286833', 22, true);
INSERT INTO public.platform_metadata VALUES ('42d5df61-fdf6-4eb2-9dac-d45d924a4388', 'auth-service', '1', '2026-10-27 01:30:38.253543+00');
ALTER TABLE ONLY public.boot3_compatibility_probe
    ADD CONSTRAINT boot3_compatibility_probe_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);
ALTER TABLE ONLY public.oauth_accounts
    ADD CONSTRAINT oauth_accounts_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.platform_metadata
    ADD CONSTRAINT platform_metadata_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.platform_metadata
    ADD CONSTRAINT platform_metadata_service_name_key UNIQUE (service_name);
ALTER TABLE ONLY public.oauth_accounts
    ADD CONSTRAINT uk_oauth_provider_subject UNIQUE (provider, provider_user_id);
ALTER TABLE ONLY public.users
    ADD CONSTRAINT uk_users_email UNIQUE (email);
ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_pkey PRIMARY KEY (user_id, role);
ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);
CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);
CREATE INDEX idx_oauth_accounts_email ON public.oauth_accounts USING btree (email);
CREATE INDEX idx_oauth_accounts_user ON public.oauth_accounts USING btree (user_id);
CREATE INDEX idx_user_roles_role ON public.user_roles USING btree (role);
CREATE INDEX idx_users_status ON public.users USING btree (status);
ALTER TABLE ONLY public.oauth_accounts
    ADD CONSTRAINT fk_oauth_accounts_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;
ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

-- Representative business record for existing-data ORM/JDBC verification.
INSERT INTO public.users(id,email,display_name,status,created_at,updated_at,version) VALUES('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb','compat@example.test','Compatibility','ACTIVE','2026-01-02T03:04:05.123456Z','2026-01-02T03:04:05.123456Z',7);
