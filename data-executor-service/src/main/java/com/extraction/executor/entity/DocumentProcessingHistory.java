package com.extraction.executor.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * JPA Entity for tracking document processing history
 * 
 * This entity maintains a complete audit trail of all document processing
 * operations,
 * allowing full traceability by transaction_id.
 */
@Entity
@Table(name = "document_processing_history", indexes = {
        @Index(name = "idx_transaction_id", columnList = "transaction_id"),
        @Index(name = "idx_created_at", columnList = "created_at"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_stage", columnList = "stage"),
        @Index(name = "idx_transaction_stage", columnList = "transaction_id, stage")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentProcessingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Transaction ID from the document processing API
     * Initially null for SPLIT_RENAME, populated after API response
     */
    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    /**
     * Processing stage (SPLIT_RENAME, CHECK_COMPLETENESS, EXTRACT_DATA)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 50)
    private ProcessingStage stage;

    /**
     * Current status (IN_PROGRESS, SUCCESS, FAILED)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProcessingStatus status;

    /**
     * Original S3 URI (only for SPLIT_RENAME stage)
     */
    @Column(name = "s3_uri", columnDefinition = "TEXT")
    private String s3Uri;

    /**
     * Request payload sent to API (stored as JSON)
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_payload", columnDefinition = "jsonb")
    private String requestPayload;

    /**
     * Response payload from API (stored as JSON)
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_payload", columnDefinition = "jsonb")
    private String responsePayload;

    /**
     * Error message if processing failed
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Stack trace for debugging (not exposed via API)
     */
    @Column(name = "error_stack_trace", columnDefinition = "TEXT")
    private String errorStackTrace;

    /**
     * Processing duration in milliseconds
     */
    @Column(name = "processing_time_ms")
    private Long processingTimeMs;

    /**
     * When the operation started
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * When the operation completed/updated
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Automatically set timestamps before persist
     */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    /**
     * Automatically update timestamp before update
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
