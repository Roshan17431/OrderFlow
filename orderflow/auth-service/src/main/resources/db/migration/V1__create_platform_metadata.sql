CREATE TABLE platform_metadata (
    id UUID PRIMARY KEY,
    service_name VARCHAR(100) NOT NULL UNIQUE,
    schema_version VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

INSERT INTO platform_metadata (id, service_name, schema_version, created_at)
VALUES ('42d5df61-fdf6-4eb2-9dac-d45d924a4388', 'auth-service', '1', CURRENT_TIMESTAMP);
