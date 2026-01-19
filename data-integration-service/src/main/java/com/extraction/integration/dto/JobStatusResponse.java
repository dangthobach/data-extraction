package com.extraction.integration.dto;

import com.extraction.integration.entity.JobStatus;
import com.extraction.integration.entity.JobType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for Job status response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobStatusResponse {

    private UUID id;
    private String requestId;
    private JobType jobType;
    private JobStatus status;
    private Integer progress;
    private String sourcePath;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;

    /**
     * Human-readable status message
     */
    private String statusMessage;
}
