package com.extraction.integration.controller;

import com.extraction.integration.dto.ApiResponse;
import com.extraction.integration.dto.JobStatusResponse;
import com.extraction.integration.dto.SystemInfo;
import com.extraction.integration.service.IamAuthService;
import com.extraction.integration.service.JobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for Job Status API.
 * Allows third-party systems to query job processing status.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class JobController {

    private final IamAuthService iamAuthService;
    private final JobService jobService;

    /**
     * Get job status by ID
     */
    @GetMapping("/{jobId}")
    public ResponseEntity<ApiResponse<JobStatusResponse>> getJobStatus(
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-Client-Secret") String clientSecret,
            @PathVariable UUID jobId) {

        // Validate Credentials
        SystemInfo systemInfo = iamAuthService.validate(clientId, clientSecret);
        String systemId = systemInfo.getSystemId();

        log.debug("Get job status: jobId={}, systemId={}", jobId, systemId);

        return jobService.getJobByIdAndSystem(jobId, systemId)
                .map(job -> ResponseEntity.ok(ApiResponse.success(job)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * List jobs for the authenticated system
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<JobStatusResponse>>> listJobs(
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-Client-Secret") String clientSecret,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // Validate Credentials
        SystemInfo systemInfo = iamAuthService.validate(clientId, clientSecret);
        String systemId = systemInfo.getSystemId();

        // Limit page size
        size = Math.min(size, 100);

        log.debug("List jobs: systemId={}, page={}, size={}", systemId, page, size);

        Page<JobStatusResponse> jobs = jobService.getJobsBySystem(systemId, page, size);
        return ResponseEntity.ok(ApiResponse.success(jobs));
    }

    /**
     * Get job statistics for the authenticated system
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getJobStats(
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-Client-Secret") String clientSecret) {

        SystemInfo systemInfo = iamAuthService.validate(clientId, clientSecret);
        String systemId = systemInfo.getSystemId();

        long pendingCount = jobService.countPendingJobs(systemId);

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "systemId", systemId,
                "dailyLimit", systemInfo.getDailyLimit() != null ? systemInfo.getDailyLimit() : 100000,
                "pendingJobs", pendingCount)));
    }
}
