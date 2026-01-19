package com.extraction.integration.entity;

/**
 * Status of a failed message
 */
public enum FailedMessageStatus {
    /**
     * Pending review/retry
     */
    PENDING,

    /**
     * Retry scheduled
     */
    RETRYING,

    /**
     * Retry succeeded
     */
    RESOLVED,

    /**
     * Discarded after max retries
     */
    DISCARDED,

    /**
     * Manually resolved
     */
    MANUAL
}
