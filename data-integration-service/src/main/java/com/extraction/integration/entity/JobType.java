package com.extraction.integration.entity;

/**
 * Type of ingestion job
 */
public enum JobType {
    /**
     * File upload from API
     */
    UPLOAD,

    /**
     * Trigger sync from external source (SFTP/S3)
     */
    SYNC
}
