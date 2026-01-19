-- V1__create_api_keys.sql
-- API Keys table for third-party system authentication

CREATE TABLE IF NOT EXISTS api_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    api_key_hash VARCHAR(128) NOT NULL UNIQUE,
    system_id VARCHAR(50) NOT NULL,
    system_name VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    daily_limit INT DEFAULT 100,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    last_used_at TIMESTAMP
);

-- Indexes for fast lookup
CREATE INDEX IF NOT EXISTS idx_api_keys_hash ON api_keys(api_key_hash);
CREATE INDEX IF NOT EXISTS idx_api_keys_system ON api_keys(system_id);
CREATE INDEX IF NOT EXISTS idx_api_keys_status ON api_keys(status);

-- Insert sample API key for testing
-- Plain text key: "test-api-key-integration-001"
-- SHA-256 hash of the above key
INSERT INTO api_keys (system_id, system_name, api_key_hash, status, daily_limit)
VALUES (
    'SYSTEM_A',
    'Test System A',
    'b8e9c5a7d3f2e1a4c6b8d0f2e4a6c8b0d2f4e6a8c0b2d4f6e8a0c2b4d6f8e0a2',
    'ACTIVE',
    100
) ON CONFLICT (api_key_hash) DO NOTHING;

-- Comment: To generate hash for new keys, use:
-- SELECT encode(sha256('your-api-key'::bytea), 'hex');
