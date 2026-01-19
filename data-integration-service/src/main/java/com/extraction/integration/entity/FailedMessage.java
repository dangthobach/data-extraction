package com.extraction.integration.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity to persist failed messages from DLQ for analysis and retry
 */
@Entity
@Table(name = "failed_messages", indexes = {
        @Index(name = "idx_failed_status", columnList = "status"),
        @Index(name = "idx_failed_created", columnList = "createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailedMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Original job ID from the message
     */
    @Column(length = 50)
    private String jobId;

    /**
     * System that sent the message
     */
    @Column(length = 50)
    private String systemId;

    /**
     * Queue from which message was dead-lettered
     */
    @Column(length = 100)
    private String originalQueue;

    /**
     * Error that caused the failure
     */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Original message payload as JSON
     */
    @Column(columnDefinition = "TEXT")
    private String messagePayload;

    /**
     * Number of retry attempts
     */
    @Builder.Default
    private Integer retryCount = 0;

    /**
     * Processing status
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private FailedMessageStatus status = FailedMessageStatus.PENDING;

    @Column(updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant processedAt;
}
