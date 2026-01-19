package com.extraction.integration.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing an ingestion job.
 * Tracks job status from creation to completion.
 */
@Entity
@Table(name = "jobs", indexes = {
        @Index(name = "idx_jobs_system", columnList = "systemId"),
        @Index(name = "idx_jobs_status", columnList = "status"),
        @Index(name = "idx_jobs_created", columnList = "createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Client request ID for correlation
     */
    @Column(nullable = false, length = 50)
    private String requestId;

    /**
     * System that created this job
     */
    @Column(nullable = false, length = 50)
    private String systemId;

    /**
     * Type of job
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobType jobType;

    /**
     * Current status
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private JobStatus status = JobStatus.PENDING;

    /**
     * Source file path (MinIO or external)
     */
    @Column(length = 500)
    private String sourcePath;

    /**
     * Error message if FAILED
     */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Progress percentage (0-100)
     */
    @Builder.Default
    private Integer progress = 0;

    @Column(updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Builder.Default
    private Instant updatedAt = Instant.now();

    private Instant completedAt;

    /**
     * Mark job as completed
     */
    public void complete() {
        this.status = JobStatus.COMPLETED;
        this.progress = 100;
        this.completedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Mark job as failed
     */
    public void fail(String errorMessage) {
        this.status = JobStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Update processing progress
     */
    public void updateProgress(int progress) {
        this.status = JobStatus.PROCESSING;
        this.progress = Math.min(100, Math.max(0, progress));
        this.updatedAt = Instant.now();
    }
}
