package com.extraction.executor.controller;

import com.extraction.executor.dto.history.DocumentProcessingHistoryDTO;
import com.extraction.executor.entity.DocumentProcessingHistory;
import com.extraction.executor.entity.ProcessingStage;
import com.extraction.executor.entity.ProcessingStatus;
import com.extraction.executor.service.DocumentProcessingHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller for querying document processing history
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/document-processing/history")
@RequiredArgsConstructor
public class DocumentProcessingHistoryController {

    private final DocumentProcessingHistoryService historyService;

    /**
     * Get all history records for a transaction ID
     * 
     * Example: GET /api/v1/document-processing/history/TRX_20260116_123410_b27ec6
     */
    @GetMapping("/{transactionId}")
    public ResponseEntity<List<DocumentProcessingHistoryDTO>> getHistory(
            @PathVariable String transactionId) {

        log.debug("Fetching history for transactionId: {}", transactionId);

        List<DocumentProcessingHistory> history = historyService.getHistoryByTransactionId(transactionId);
        List<DocumentProcessingHistoryDTO> dtos = history.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * Get the latest history record for a transaction
     *
     * Example: GET
     * /api/v1/document-processing/history/TRX_20260116_123410_b27ec6/latest
     */
    @GetMapping("/{transactionId}/latest")
    public ResponseEntity<DocumentProcessingHistoryDTO> getLatestHistory(
            @PathVariable String transactionId) {

        log.debug("Fetching latest history for transactionId: {}", transactionId);

        return historyService.getLatestByTransactionId(transactionId)
                .map(this::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get history for a specific stage of a transaction
     * 
     * Example: GET
     * /api/v1/document-processing/history/TRX_20260116_123410_b27ec6/stage/SPLIT_RENAME
     */
    @GetMapping("/{transactionId}/stage/{stage}")
    public ResponseEntity<DocumentProcessingHistoryDTO> getHistoryByStage(
            @PathVariable String transactionId,
            @PathVariable ProcessingStage stage) {

        log.debug("Fetching history for transactionId: {}, stage: {}", transactionId, stage);

        return historyService.getHistoryByTransactionIdAndStage(transactionId, stage)
                .map(this::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all history records by status
     * 
     * Example: GET /api/v1/document-processing/history/search?status=FAILED
     */
    @GetMapping("/search")
    public ResponseEntity<List<DocumentProcessingHistoryDTO>> searchHistory(
            @RequestParam(required = false) ProcessingStatus status) {

        log.debug("Searching history with status: {}", status);

        if (status != null) {
            List<DocumentProcessingHistory> history = historyService.getHistoryByStatus(status);
            List<DocumentProcessingHistoryDTO> dtos = history.stream()
                    .map(this::toDTO)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(dtos);
        }

        return ResponseEntity.badRequest().build();
    }

    /**
     * Convert entity to DTO (excludes stack trace)
     */
    private DocumentProcessingHistoryDTO toDTO(DocumentProcessingHistory entity) {
        return DocumentProcessingHistoryDTO.builder()
                .id(entity.getId())
                .transactionId(entity.getTransactionId())
                .stage(entity.getStage())
                .status(entity.getStatus())
                .s3Uri(entity.getS3Uri())
                .requestPayload(entity.getRequestPayload())
                .responsePayload(entity.getResponsePayload())
                .errorMessage(entity.getErrorMessage())
                .processingTimeMs(entity.getProcessingTimeMs())
                .processingTimeHuman(DocumentProcessingHistoryDTO.formatDuration(entity.getProcessingTimeMs()))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
