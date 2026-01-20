package com.extraction.integration.controller;

import com.extraction.integration.dto.ApiResponse;
import com.extraction.integration.dto.IngestRequestMessage;
import com.extraction.integration.dto.SystemInfo;
import com.extraction.integration.dto.TriggerJobRequest;
import com.extraction.integration.service.IamAuthService;
import com.extraction.integration.service.MessagePublisherService;
import com.extraction.integration.service.MinioStorageService;
import com.extraction.integration.service.RateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class IngestController {

        private final RateLimitService rateLimitService;
        private final MinioStorageService minioStorageService;
        private final MessagePublisherService messagePublisherService;
        private final IamAuthService iamAuthService;
        private final ObjectMapper objectMapper;

        /**
         * Upload file for processing
         * Flow: Upload to MinIO -> Publish message to RabbitMQ
         * 
         * Protected by:
         * - Rate Limiting (100/day per system)
         * - Bulkhead (max 30 concurrent uploads)
         * - Circuit Breaker (on MinIO and RabbitMQ services)
         */
        @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @Bulkhead(name = "uploadBulkhead", fallbackMethod = "uploadBulkheadFallback")
        public ResponseEntity<ApiResponse<Map<String, String>>> uploadFile(
                        @RequestHeader("X-Client-Id") String clientId,
                        @RequestHeader("X-Client-Secret") String clientSecret,
                        @RequestParam("file") MultipartFile file) {

                // Validate Credentials
                SystemInfo systemInfo = iamAuthService.validate(clientId, clientSecret);
                String systemId = systemInfo.getSystemId();

                String requestId = UUID.randomUUID().toString();
                log.info("Received upload request: requestId={}, systemId={}, file={}, size={}, cacheL1={}, cacheL2={}",
                                requestId, systemId, file.getOriginalFilename(), file.getSize(),
                                systemInfo.isCachedL1(), systemInfo.isCachedL2());

                // Rate limit check (uses system-specific limit if configured)
                if (!rateLimitService.checkAndIncrementRateLimit(systemId, systemInfo.getDailyLimit())) {
                        log.warn("Rate limit exceeded for system: {}", systemId);
                        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                                        .body(ApiResponse.error(
                                                        "Daily request limit exceeded. Remaining: 0",
                                                        "RATE_LIMIT_EXCEEDED"));
                }

                try {
                        // Upload to MinIO temp bucket (Circuit Breaker protected)
                        String minioPath = minioStorageService.uploadToTemp(file, systemId);

                        // Create and publish message (Circuit Breaker protected)
                        IngestRequestMessage message = IngestRequestMessage.builder()
                                        .requestId(requestId)
                                        .systemId(systemId)
                                        .type(IngestRequestMessage.RequestType.UPLOAD)
                                        .sourcePath(minioPath)
                                        .build();

                        String jobId = messagePublisherService.publishIngestRequest(message);

                        log.info("Upload accepted: jobId={}, requestId={}", jobId, requestId);

                        return ResponseEntity.accepted()
                                        .body(ApiResponse.success("File upload accepted for processing",
                                                        Map.of(
                                                                        "jobId", jobId,
                                                                        "requestId", requestId,
                                                                        "remaining",
                                                                        String.valueOf(rateLimitService
                                                                                        .getRemainingQuota(
                                                                                                        systemId)))));

                } catch (RuntimeException e) {
                        // Circuit breaker or service error
                        log.error("Service error during upload: {}", e.getMessage());
                        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                                        .body(ApiResponse.error(e.getMessage(), "SERVICE_UNAVAILABLE"));
                } catch (Exception e) {
                        log.error("Failed to process upload: {}", e.getMessage(), e);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(ApiResponse.error("Failed to process upload: " + e.getMessage(),
                                                        "UPLOAD_ERROR"));
                }
        }

        /**
         * Bulkhead fallback when max concurrent uploads reached
         */
        public ResponseEntity<ApiResponse<Map<String, String>>> uploadBulkheadFallback(
                        String apiKey, MultipartFile file, Throwable throwable) {
                log.warn("Upload bulkhead full, rejecting request for file: {}",
                                file.getOriginalFilename());
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                                .body(ApiResponse.error(
                                                "Server is busy processing other requests. Please retry in a few seconds.",
                                                "BULKHEAD_FULL"));
        }

        /**
         * Trigger a job to pull files from external source (SFTP/S3)
         * 
         * Protected by:
         * - Rate Limiting (100/day per system)
         * - Bulkhead (max 50 concurrent triggers)
         * - Circuit Breaker (on RabbitMQ service)
         */
        @PostMapping("/job/trigger")
        @Bulkhead(name = "triggerBulkhead", fallbackMethod = "triggerBulkheadFallback")
        public ResponseEntity<ApiResponse<Map<String, String>>> triggerJob(
                        @RequestHeader("X-Client-Id") String clientId,
                        @RequestHeader("X-Client-Secret") String clientSecret,
                        @Valid @RequestBody TriggerJobRequest request) {

                // Validate Credentials
                SystemInfo systemInfo = iamAuthService.validate(clientId, clientSecret);
                String systemId = systemInfo.getSystemId();

                String requestId = UUID.randomUUID().toString();
                log.info("Received trigger request: requestId={}, systemId={}, sourceType={}, cacheL1={}, cacheL2={}",
                                requestId, systemId, request.getSourceType(),
                                systemInfo.isCachedL1(), systemInfo.isCachedL2());

                // Rate limit check
                if (!rateLimitService.checkAndIncrementRateLimit(systemId, systemInfo.getDailyLimit())) {
                        log.warn("Rate limit exceeded for system: {}", systemId);
                        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                                        .body(ApiResponse.error(
                                                        "Daily request limit exceeded. Remaining: 0",
                                                        "RATE_LIMIT_EXCEEDED"));
                }

                try {
                        // Create and publish message (Circuit Breaker protected)
                        IngestRequestMessage message = IngestRequestMessage.builder()
                                        .requestId(requestId)
                                        .systemId(systemId)
                                        .type(IngestRequestMessage.RequestType.SYNC)
                                        .sourcePath(request.getRemotePath())
                                        .sourceConfig(objectMapper.writeValueAsString(request))
                                        .build();

                        String jobId = messagePublisherService.publishIngestRequest(message);

                        log.info("Job triggered: jobId={}, requestId={}", jobId, requestId);

                        return ResponseEntity.accepted()
                                        .body(ApiResponse.success("Job triggered successfully",
                                                        Map.of(
                                                                        "jobId", jobId,
                                                                        "requestId", requestId,
                                                                        "remaining",
                                                                        String.valueOf(rateLimitService
                                                                                        .getRemainingQuota(
                                                                                                        systemId)))));

                } catch (RuntimeException e) {
                        // Circuit breaker or service error
                        log.error("Service error during trigger: {}", e.getMessage());
                        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                                        .body(ApiResponse.error(e.getMessage(), "SERVICE_UNAVAILABLE"));
                } catch (Exception e) {
                        log.error("Failed to trigger job: {}", e.getMessage(), e);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(ApiResponse.error("Failed to trigger job: " + e.getMessage(),
                                                        "TRIGGER_ERROR"));
                }
        }

        /**
         * Bulkhead fallback when max concurrent triggers reached
         */
        public ResponseEntity<ApiResponse<Map<String, String>>> triggerBulkheadFallback(
                        String apiKey, TriggerJobRequest request, Throwable throwable) {
                log.warn("Trigger bulkhead full, rejecting request");
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                                .body(ApiResponse.error(
                                                "Server is busy processing other requests. Please retry in a few seconds.",
                                                "BULKHEAD_FULL"));
        }

        /**
         * Get rate limit status for a system
         */
        /**
         * Get rate limit status for a system
         */
        @GetMapping("/quota")
        public ResponseEntity<ApiResponse<Map<String, Object>>> getQuota(
                        @RequestHeader("X-Client-Id") String clientId,
                        @RequestHeader("X-Client-Secret") String clientSecret) {

                SystemInfo systemInfo = iamAuthService.validate(clientId, clientSecret);
                String systemId = systemInfo.getSystemId();

                int used = rateLimitService.getCurrentUsage(systemId, systemInfo.getDailyLimit());
                int remaining = rateLimitService.getRemainingQuota(systemId, systemInfo.getDailyLimit());
                int limit = systemInfo.getDailyLimit() != null ? systemInfo.getDailyLimit() : 100;

                return ResponseEntity.ok(ApiResponse.success(Map.of(
                                "systemId", systemId,
                                "systemName", systemInfo.getSystemName() != null ? systemInfo.getSystemName() : "N/A",
                                "used", used,
                                "remaining", remaining,
                                "limit", limit,
                                "cacheStats", iamAuthService.getCacheStats())));
        }

        /**
         * Health check endpoint for circuit breaker status
         */
        @GetMapping("/health/resilience")
        public ResponseEntity<ApiResponse<String>> getResilienceHealth() {
                return ResponseEntity.ok(ApiResponse.success("Resilience4j is active"));
        }
}
