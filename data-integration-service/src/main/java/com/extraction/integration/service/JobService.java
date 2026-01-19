package com.extraction.integration.service;

import com.extraction.integration.dto.JobStatusResponse;
import com.extraction.integration.entity.Job;
import com.extraction.integration.entity.JobStatus;
import com.extraction.integration.entity.JobType;
import com.extraction.integration.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for Job lifecycle management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;

    /**
     * Create a new job
     */
    @Transactional
    public Job createJob(String requestId, String systemId, JobType jobType, String sourcePath) {
        Job job = Job.builder()
                .requestId(requestId)
                .systemId(systemId)
                .jobType(jobType)
                .sourcePath(sourcePath)
                .status(JobStatus.PENDING)
                .progress(0)
                .build();

        job = jobRepository.save(job);
        log.info("Job created: id={}, requestId={}, systemId={}, type={}",
                job.getId(), requestId, systemId, jobType);
        return job;
    }

    /**
     * Get job by ID
     */
    public Optional<JobStatusResponse> getJobById(UUID jobId) {
        return jobRepository.findById(jobId)
                .map(this::toResponse);
    }

    /**
     * Get job by ID and verify ownership
     */
    public Optional<JobStatusResponse> getJobByIdAndSystem(UUID jobId, String systemId) {
        return jobRepository.findById(jobId)
                .filter(job -> job.getSystemId().equals(systemId))
                .map(this::toResponse);
    }

    /**
     * List jobs for a system with pagination
     */
    public Page<JobStatusResponse> getJobsBySystem(String systemId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return jobRepository.findBySystemIdOrderByCreatedAtDesc(systemId, pageable)
                .map(this::toResponse);
    }

    /**
     * Update job status
     */
    @Transactional
    public void updateStatus(UUID jobId, JobStatus status) {
        jobRepository.updateStatus(jobId, status, Instant.now());
        log.debug("Job {} status updated to {}", jobId, status);
    }

    /**
     * Update job progress
     */
    @Transactional
    public void updateProgress(UUID jobId, int progress) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.updateProgress(progress);
            jobRepository.save(job);
        });
    }

    /**
     * Mark job as completed
     */
    @Transactional
    public void completeJob(UUID jobId) {
        jobRepository.markCompleted(jobId, Instant.now());
        log.info("Job {} completed", jobId);
    }

    /**
     * Mark job as failed
     */
    @Transactional
    public void failJob(UUID jobId, String errorMessage) {
        jobRepository.markFailed(jobId, errorMessage, Instant.now());
        log.warn("Job {} failed: {}", jobId, errorMessage);
    }

    /**
     * Count pending jobs for a system
     */
    public long countPendingJobs(String systemId) {
        return jobRepository.countBySystemIdAndStatus(systemId, JobStatus.PENDING);
    }

    // ==================== Private Methods ====================

    private JobStatusResponse toResponse(Job job) {
        return JobStatusResponse.builder()
                .id(job.getId())
                .requestId(job.getRequestId())
                .jobType(job.getJobType())
                .status(job.getStatus())
                .progress(job.getProgress())
                .sourcePath(job.getSourcePath())
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .completedAt(job.getCompletedAt())
                .statusMessage(getStatusMessage(job))
                .build();
    }

    private String getStatusMessage(Job job) {
        return switch (job.getStatus()) {
            case PENDING -> "Job is queued for processing";
            case PROCESSING -> String.format("Processing in progress (%d%%)", job.getProgress());
            case COMPLETED -> "Job completed successfully";
            case FAILED -> "Job failed: " + (job.getErrorMessage() != null ? job.getErrorMessage() : "Unknown error");
        };
    }
}
