package com.extraction.integration.entity;

/**
 * Status of a processing job
 */
public enum JobStatus {
    /**
     * Job created, waiting to be picked up by Executor
     */
    PENDING,

    /**
     * Job is being processed by Executor/ETL
     */
    PROCESSING,

    /**
     * Job completed successfully
     */
    COMPLETED,

    /**
     * Job failed - check errorMessage
     */
    FAILED
}
