package com.extraction.executor.service;

import com.extraction.executor.client.DocumentProcessingClient;
import com.extraction.executor.dto.document.*;
import com.extraction.executor.entity.DocumentProcessingHistory;
import com.extraction.executor.entity.ProcessingStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for orchestrating Document Processing Pipeline
 * 
 * Manages the stateful pipeline flow:
 * 1. Split & Rename: Process compressed file and identify documents
 * 2. Check Completeness: Verify all required documents are present
 * 3. Extract Data: Extract structured data from documents
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentProcessingService {

    private final DocumentProcessingClient documentProcessingClient;
    private final DocumentProcessingHistoryService historyService;

    /**
     * Stage 1: Split and Rename Documents
     * 
     * Initiates the pipeline by processing a compressed file from S3.
     * Returns a transaction ID that must be used for subsequent stages.
     * 
     * @param s3Uri S3 URI of the compressed file (e.g.,
     *              "s3://bucket/path/file.zip")
     * @return SplitRenameResponse containing transaction ID and processed documents
     * @throws RuntimeException if API call fails
     */
    public SplitRenameResponse splitAndRename(String s3Uri) {
        log.info("Starting split-rename operation for S3 URI: {}", s3Uri);

        SplitRenameRequest request = SplitRenameRequest.builder()
                .s3Uri(s3Uri)
                .build();

        // Create IN_PROGRESS history record
        DocumentProcessingHistory history = historyService.createHistory(
                null, ProcessingStage.SPLIT_RENAME, s3Uri, request);

        try {
            long startTime = System.currentTimeMillis();
            SplitRenameResponse response = documentProcessingClient.splitAndRename(request);
            long duration = System.currentTimeMillis() - startTime;

            // Update to SUCCESS
            historyService.updateSuccess(history.getId(), response.getTransactionId(), response, duration);

            log.info("Split-rename completed successfully. Transaction ID: {}, Documents processed: {}",
                    response.getTransactionId(),
                    response.getSubDocumentsProcessed());

            if (response.getResults() != null) {
                response.getResults().forEach(doc -> log.debug("  - {}: {} (saved to: {})",
                        doc.getCategory(),
                        doc.getName(),
                        doc.getSavedPath()));
            }

            return response;
        } catch (Exception e) {
            historyService.updateFailure(history.getId(), e.getMessage(), e);
            log.error("Failed to split and rename documents for S3 URI: {}", s3Uri, e);
            throw new RuntimeException("Split and rename operation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Stage 2: Check Document Completeness
     * 
     * Verifies that all required documents are present for the disbursement
     * product type.
     * 
     * @param transactionId Transaction ID from split-rename stage
     * @return CheckCompletenessResponse with status and missing/available documents
     * @throws RuntimeException if API call fails
     */
    public CheckCompletenessResponse checkCompleteness(String transactionId) {
        log.info("Checking document completeness for transaction: {}", transactionId);

        CheckCompletenessRequest request = CheckCompletenessRequest.builder()
                .transactionId(transactionId)
                .build();

        // Create IN_PROGRESS history record
        DocumentProcessingHistory history = historyService.createHistory(
                transactionId, ProcessingStage.CHECK_COMPLETENESS, null, request);

        try {
            long startTime = System.currentTimeMillis();
            CheckCompletenessResponse response = documentProcessingClient.checkCompleteness(request);
            long duration = System.currentTimeMillis() - startTime;

            // Update to SUCCESS
            historyService.updateSuccess(history.getId(), transactionId, response, duration);

            String status = response.getCheckResult() != null ? response.getCheckResult().getStatus() : "unknown";

            log.info("Completeness check completed. Status: {}", status);

            if (response.getCheckResult() != null) {
                if (response.getCheckResult().getMissingDocuments() != null &&
                        !response.getCheckResult().getMissingDocuments().isEmpty()) {
                    log.warn("Missing documents: {}", response.getCheckResult().getMissingDocuments());
                }

                if (response.getCheckResult().getAvailableDocuments() != null) {
                    log.debug("Available documents count: {}",
                            response.getCheckResult().getAvailableDocuments().size());
                }
            }

            return response;
        } catch (Exception e) {
            historyService.updateFailure(history.getId(), e.getMessage(), e);
            log.error("Failed to check completeness for transaction: {}", transactionId, e);
            throw new RuntimeException("Completeness check failed: " + e.getMessage(), e);
        }
    }

    /**
     * Stage 3: Extract Structured Data
     * 
     * Extracts structured data from all disbursement documents using OCR
     * and AI-based data extraction.
     * 
     * @param transactionId Transaction ID from split-rename stage
     * @return ExtractDataResponse with all extracted structured data
     * @throws RuntimeException if API call fails
     */
    public ExtractDataResponse extractData(String transactionId) {
        log.info("Extracting data for transaction: {}", transactionId);

        ExtractDataRequest request = ExtractDataRequest.builder()
                .transactionId(transactionId)
                .build();

        // Create IN_PROGRESS history record
        DocumentProcessingHistory history = historyService.createHistory(
                transactionId, ProcessingStage.EXTRACT_DATA, null, request);

        try {
            long startTime = System.currentTimeMillis();
            ExtractDataResponse response = documentProcessingClient.extractData(request);
            long duration = System.currentTimeMillis() - startTime;

            // Update to SUCCESS
            historyService.updateSuccess(history.getId(), transactionId, response, duration);

            log.info("Data extraction completed. Status: {}", response.getStatus());

            if (response.getExtractedDetails() != null) {
                logExtractedDataSummary(response.getExtractedDetails());
            }

            return response;
        } catch (Exception e) {
            historyService.updateFailure(history.getId(), e.getMessage(), e);
            log.error("Failed to extract data for transaction: {}", transactionId, e);
            throw new RuntimeException("Data extraction failed: " + e.getMessage(), e);
        }
    }

    /**
     * Execute complete pipeline: Split → Check → Extract
     * 
     * Orchestrates all three stages in sequence. This is a convenience method
     * for processing documents end-to-end.
     * 
     * @param s3Uri S3 URI of the compressed file
     * @return ExtractDataResponse with final extracted data
     * @throws RuntimeException if any stage fails
     */
    public ExtractDataResponse processFullPipeline(String s3Uri) {
        log.info("Starting full document processing pipeline for S3 URI: {}", s3Uri);

        try {
            // Stage 1: Split and Rename
            SplitRenameResponse splitResponse = splitAndRename(s3Uri);
            String transactionId = splitResponse.getTransactionId();

            log.info("Pipeline Stage 1/3 completed: Split & Rename");

            // Stage 2: Check Completeness
            CheckCompletenessResponse completenessResponse = checkCompleteness(transactionId);

            log.info("Pipeline Stage 2/3 completed: Check Completeness");

            // Log warning if documents are incomplete but continue to extraction
            if (completenessResponse.getCheckResult() != null &&
                    "incompleted".equals(completenessResponse.getCheckResult().getStatus())) {
                log.warn("Documents are incomplete but proceeding with extraction. " +
                        "Missing: {}", completenessResponse.getCheckResult().getMissingDocuments());
            }

            // Stage 3: Extract Data
            ExtractDataResponse extractResponse = extractData(transactionId);

            log.info("Pipeline Stage 3/3 completed: Extract Data");
            log.info("Full pipeline completed successfully for transaction: {}", transactionId);

            return extractResponse;

        } catch (Exception e) {
            log.error("Full pipeline failed for S3 URI: {}", s3Uri, e);
            throw new RuntimeException("Full pipeline processing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Log summary of extracted data (for debugging)
     */
    private void logExtractedDataSummary(ExtractDataResponse.ExtractedDetails details) {
        log.debug("Extracted data summary:");

        if (details.getToTrinh() != null) {
            log.debug("  - Tờ trình: Customer {}, Amount {}",
                    details.getToTrinh().getTenKhachHang(),
                    details.getToTrinh().getSoTienGiaiNgan());
        }

        if (details.getNghiQuyet() != null) {
            log.debug("  - Nghị quyết: Customer {}, Credit limit {}",
                    details.getNghiQuyet().getTenKhachHang(),
                    details.getNghiQuyet().getGiaTriHanMucVay());
        }

        if (details.getKheUoc() != null) {
            log.debug("  - Khế ước: Customer {}, Disbursement amount {}",
                    details.getKheUoc().getTenKhachHang(),
                    details.getKheUoc().getSoTienDeNghiGiaiNgan());
        }

        if (details.getHopDongMuaBan() != null) {
            log.debug("  - Hợp đồng: Contract {}, Parties {} <-> {}",
                    details.getHopDongMuaBan().getSoHopDong(),
                    details.getHopDongMuaBan().getTenBenBan(),
                    details.getHopDongMuaBan().getTenBenMua());
        }

        if (details.getDeNghiThanhToan() != null) {
            log.debug("  - Đề nghị thanh toán: Amount {}",
                    details.getDeNghiThanhToan().getSoTienDeNghiThanhToan());
        }
    }
}
