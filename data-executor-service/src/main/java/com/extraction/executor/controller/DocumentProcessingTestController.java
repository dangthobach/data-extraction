package com.extraction.executor.controller;

import com.extraction.executor.dto.document.*;
import com.extraction.executor.service.DocumentProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for testing Document Processing Integration
 * 
 * This controller provides endpoints to manually test the document processing
 * pipeline.
 * In production, these operations would typically be triggered by RabbitMQ
 * consumers.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/test/document-processing")
@RequiredArgsConstructor
public class DocumentProcessingTestController {

    private final DocumentProcessingService documentProcessingService;

    /**
     * Test endpoint for Stage 1: Split and Rename
     * 
     * Example:
     * POST /api/v1/test/document-processing/split-rename
     * {
     * "s3_uri": "s3://s3-vpbank-model/giai-ngan/2-15-Sao Viet Linh-ELD-33455.zip"
     * }
     */
    @PostMapping("/split-rename")
    public ResponseEntity<SplitRenameResponse> splitAndRename(@RequestBody SplitRenameRequest request) {
        log.info("Test: Split and rename for S3 URI: {}", request.getS3Uri());

        SplitRenameResponse response = documentProcessingService.splitAndRename(request.getS3Uri());

        return ResponseEntity.ok(response);
    }

    /**
     * Test endpoint for Stage 2: Check Completeness
     * 
     * Example:
     * POST /api/v1/test/document-processing/check-completeness
     * {
     * "transaction_id": "TRX_20260116_123410_b27ec6"
     * }
     */
    @PostMapping("/check-completeness")
    public ResponseEntity<CheckCompletenessResponse> checkCompleteness(@RequestBody CheckCompletenessRequest request) {
        log.info("Test: Check completeness for transaction: {}", request.getTransactionId());

        CheckCompletenessResponse response = documentProcessingService.checkCompleteness(request.getTransactionId());

        return ResponseEntity.ok(response);
    }

    /**
     * Test endpoint for Stage 3: Extract Data
     * 
     * Example:
     * POST /api/v1/test/document-processing/extract-data
     * {
     * "transaction_id": "TRX_20260116_123410_b27ec6"
     * }
     */
    @PostMapping("/extract-data")
    public ResponseEntity<ExtractDataResponse> extractData(@RequestBody ExtractDataRequest request) {
        log.info("Test: Extract data for transaction: {}", request.getTransactionId());

        ExtractDataResponse response = documentProcessingService.extractData(request.getTransactionId());

        return ResponseEntity.ok(response);
    }

    /**
     * Test endpoint for Full Pipeline
     * 
     * Executes all 3 stages: Split → Check → Extract
     * 
     * Example:
     * POST /api/v1/test/document-processing/full-pipeline?s3Uri=s3://...
     */
    @PostMapping("/full-pipeline")
    public ResponseEntity<ExtractDataResponse> fullPipeline(@RequestParam String s3Uri) {
        log.info("Test: Full pipeline for S3 URI: {}", s3Uri);

        ExtractDataResponse response = documentProcessingService.processFullPipeline(s3Uri);

        return ResponseEntity.ok(response);
    }
}
