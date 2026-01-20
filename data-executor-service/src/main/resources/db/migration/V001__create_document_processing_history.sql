-- Flyway migration to create document_processing_history table
-- Version: V001
-- Description: Create document processing history tracking table

-- Create the main table
CREATE TABLE document_processing_history (
    id BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(100),
    stage VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    s3_uri TEXT,
    request_payload JSONB,
    response_payload JSONB,
    error_message TEXT,
    error_stack_trace TEXT,
    processing_time_ms BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for fast queries
CREATE INDEX idx_transaction_id ON document_processing_history(transaction_id);
CREATE INDEX idx_created_at ON document_processing_history(created_at DESC);
CREATE INDEX idx_status ON document_processing_history(status);
CREATE INDEX idx_stage ON document_processing_history(stage);

-- Composite index for common query pattern (transaction_id + stage)
CREATE INDEX idx_transaction_stage ON document_processing_history(transaction_id, stage);

-- Add comments for documentation
COMMENT ON TABLE document_processing_history IS 'Tracks all document processing operations for audit and tracing';
COMMENT ON COLUMN document_processing_history.transaction_id IS 'Transaction ID from document processing API';
COMMENT ON COLUMN document_processing_history.stage IS 'Processing stage: SPLIT_RENAME, CHECK_COMPLETENESS, EXTRACT_DATA';
COMMENT ON COLUMN document_processing_history.status IS 'Current status: IN_PROGRESS, SUCCESS, FAILED';
COMMENT ON COLUMN document_processing_history.s3_uri IS 'Original S3 URI (only for SPLIT_RENAME stage)';
COMMENT ON COLUMN document_processing_history.request_payload IS 'Request JSON sent to API';
COMMENT ON COLUMN document_processing_history.response_payload IS 'Response JSON from API';
COMMENT ON COLUMN document_processing_history.processing_time_ms IS 'Processing duration in milliseconds';
