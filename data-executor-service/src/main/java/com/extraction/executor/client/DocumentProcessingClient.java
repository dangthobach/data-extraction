package com.extraction.executor.client;

import com.extraction.executor.config.FeignClientConfig;
import com.extraction.executor.dto.document.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * OpenFeign client for Document Processing API
 * 
 * Supports flexible URL configuration via ${document-processing.api.base-url}
 * which can be:
 * - Service name for service discovery: "http://document-processing-service"
 * - Direct URL: "http://localhost:8089"
 * - DNS name: "http://doc-processing.example.com"
 */
@FeignClient(name = "document-processing-client", url = "${document-processing.api.base-url}", configuration = FeignClientConfig.class)
public interface DocumentProcessingClient {

    /**
     * Stage 1: Split and Rename Documents
     * 
     * Receives compressed file from S3, extracts documents, identifies each
     * document type using AI, and saves them back to S3 with proper naming.
     * 
     * @param request Contains S3 URI of the compressed file
     * @return Response with transaction ID and processed documents list
     */
    @PostMapping("/api/v1/documents/split-rename")
    @CircuitBreaker(name = "documentProcessing", fallbackMethod = "splitRenameFallback")
    @Retry(name = "documentProcessing")
    SplitRenameResponse splitAndRename(@RequestBody SplitRenameRequest request);

    /**
     * Stage 2: Check Document Completeness
     * 
     * Compares available documents against required checklist for the
     * disbursement product type.
     * 
     * @param request Contains transaction ID from previous stage
     * @return Response with completion status and missing/available documents
     */
    @PostMapping("/api/v1/documents/check-completeness")
    @CircuitBreaker(name = "documentProcessing", fallbackMethod = "checkCompletenessFallback")
    @Retry(name = "documentProcessing")
    CheckCompletenessResponse checkCompleteness(@RequestBody CheckCompletenessRequest request);

    /**
     * Stage 3: Extract Structured Data
     * 
     * Performs OCR and data extraction to retrieve structured information
     * from all disbursement documents.
     * 
     * @param request Contains transaction ID from previous stage
     * @return Response with all extracted data from various document types
     */
    @PostMapping("/api/v1/documents/extract-data")
    @CircuitBreaker(name = "documentProcessing", fallbackMethod = "extractDataFallback")
    @Retry(name = "documentProcessing")
    ExtractDataResponse extractData(@RequestBody ExtractDataRequest request);

    // Fallback methods for circuit breaker

    /**
     * Fallback method for splitAndRename when circuit is open or service fails
     */
    default SplitRenameResponse splitRenameFallback(SplitRenameRequest request, Exception ex) {
        throw new RuntimeException("Document Processing API is currently unavailable for split-rename operation. " +
                "Please try again later. Error: " + ex.getMessage(), ex);
    }

    /**
     * Fallback method for checkCompleteness when circuit is open or service fails
     */
    default CheckCompletenessResponse checkCompletenessFallback(CheckCompletenessRequest request, Exception ex) {
        throw new RuntimeException("Document Processing API is currently unavailable for completeness check. " +
                "Please try again later. Error: " + ex.getMessage(), ex);
    }

    /**
     * Fallback method for extractData when circuit is open or service fails
     */
    default ExtractDataResponse extractDataFallback(ExtractDataRequest request, Exception ex) {
        throw new RuntimeException("Document Processing API is currently unavailable for data extraction. " +
                "Please try again later. Error: " + ex.getMessage(), ex);
    }
}
