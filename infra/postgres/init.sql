-- Data Extraction Pipeline - Database Initialization Script

-- Create extension for UUID
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ==========================================
-- Integration Request Logs (for auditing)
-- ==========================================
CREATE TABLE IF NOT EXISTS integration_request_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    request_id VARCHAR(50) NOT NULL,
    system_id VARCHAR(50) NOT NULL,
    api_key_hash VARCHAR(64),
    status VARCHAR(20) NOT NULL,  -- ACCEPTED, RATE_LIMITED, INVALID_AUTH
    client_ip VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_integration_logs_system_id ON integration_request_logs(system_id);
CREATE INDEX idx_integration_logs_created_at ON integration_request_logs(created_at);

-- ==========================================
-- File Processing Jobs
-- ==========================================
CREATE TABLE IF NOT EXISTS file_jobs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    job_id VARCHAR(50) UNIQUE NOT NULL,
    source_type VARCHAR(20) NOT NULL,  -- UPLOAD, SFTP, S3
    source_path VARCHAR(500),
    minio_path VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING, DOWNLOADING, PROCESSING, COMPLETED, FAILED
    file_count INTEGER DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_file_jobs_status ON file_jobs(status);
CREATE INDEX idx_file_jobs_created_at ON file_jobs(created_at);

-- ==========================================
-- Extracted Data (Raw)
-- ==========================================
CREATE TABLE IF NOT EXISTS extracted_data (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    job_id VARCHAR(50) NOT NULL REFERENCES file_jobs(job_id),
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(20),  -- PDF, EXCEL, CSV, etc.
    content_json JSONB,
    raw_text TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_extracted_data_job_id ON extracted_data(job_id);

-- ==========================================
-- Master Data (Validated & Deduplicated)
-- ==========================================
CREATE TABLE IF NOT EXISTS master_data (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    entity_type VARCHAR(50) NOT NULL,
    entity_key VARCHAR(255) NOT NULL,
    data JSONB NOT NULL,
    source_job_id VARCHAR(50),
    is_valid BOOLEAN DEFAULT TRUE,
    validation_errors TEXT[],
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(entity_type, entity_key)
);

CREATE INDEX idx_master_data_entity_type ON master_data(entity_type);
CREATE INDEX idx_master_data_entity_key ON master_data(entity_key);

-- ==========================================
-- API Keys (for Integration Service)
-- ==========================================
CREATE TABLE IF NOT EXISTS api_keys (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    system_id VARCHAR(50) UNIQUE NOT NULL,
    api_key_hash VARCHAR(64) NOT NULL,
    description VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE,
    daily_limit INTEGER DEFAULT 100,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_api_keys_system_id ON api_keys(system_id);

-- Insert sample API key for testing (key: "test-api-key-12345")
INSERT INTO api_keys (system_id, api_key_hash, description)
VALUES ('SYSTEM_A', 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855', 'Test System A')
ON CONFLICT (system_id) DO NOTHING;
