-- V3__create_failed_messages.sql
-- Failed messages table for DLQ persistence

CREATE TABLE IF NOT EXISTS failed_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id VARCHAR(50),
    system_id VARCHAR(50),
    original_queue VARCHAR(100),
    error_message TEXT,
    message_payload TEXT,
    retry_count INT DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP
);

-- Indexes for failed message processing
CREATE INDEX IF NOT EXISTS idx_failed_status ON failed_messages(status);
CREATE INDEX IF NOT EXISTS idx_failed_created ON failed_messages(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_failed_job ON failed_messages(job_id);
