CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(100),
    display_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    token_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_email_normalized CHECK (email = LOWER(BTRIM(email))),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED')),
    CONSTRAINT ck_users_token_version CHECK (token_version >= 0)
);

CREATE INDEX idx_users_status ON users (status);

CREATE TABLE user_roles (
    user_id UUID NOT NULL,
    role VARCHAR(32) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_user_roles_role CHECK (
        role IN ('CUSTOMER', 'VENDOR', 'DELIVERY_AGENT', 'ADMIN')
    )
);

CREATE INDEX idx_user_roles_role ON user_roles (role);

CREATE TABLE oauth_accounts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    email VARCHAR(320) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_oauth_accounts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_oauth_provider_subject UNIQUE (provider, provider_user_id),
    CONSTRAINT ck_oauth_provider CHECK (provider IN ('GOOGLE', 'GITHUB')),
    CONSTRAINT ck_oauth_email_normalized CHECK (email = LOWER(BTRIM(email)))
);

CREATE INDEX idx_oauth_accounts_user ON oauth_accounts (user_id);
CREATE INDEX idx_oauth_accounts_email ON oauth_accounts (email);
