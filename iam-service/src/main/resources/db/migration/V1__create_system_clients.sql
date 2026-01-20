CREATE SCHEMA IF NOT EXISTS iam_schema;

CREATE TABLE iam_schema.system_clients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id VARCHAR(50) NOT NULL UNIQUE,
    client_secret_hash VARCHAR(128) NOT NULL,
    client_name VARCHAR(100),
    scopes VARCHAR(255) DEFAULT 'read,write',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    daily_limit INTEGER DEFAULT 100000,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_system_clients_client_id ON iam_schema.system_clients(client_id);
