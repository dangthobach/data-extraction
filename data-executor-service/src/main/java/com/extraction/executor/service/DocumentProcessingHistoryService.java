package com.extraction.executor.service;

import com.extraction.executor.entity.DocumentProcessingHistory;
import com.extraction.executor.entity.ProcessingStage;
import com.extraction.executor.entity.ProcessingStatus;
import com.extraction.executor.repository.DocumentProcessingHistoryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing document processing history
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentProcessingHistoryService {

    private final DocumentProcessingHistoryRepository historyRepository;
    private final ObjectMapper objectMapper;

    /**
     * Create a new IN_PROGRESS history record
     * 
     * @param transactionId Transaction ID (null for SPLIT_RENAME initially)
     * @param stage         Processing stage
     * @param s3Uri         S3 URI (only for SPLIT_RENAME)
     * @param request       Request object
     * @return Created history record
     */
    @Transactional
    public DocumentProcessingHistory createHistory(
            String transactionId,
            ProcessingStage stage,
            String s3Uri,
            Object request) {

        log.debug("Creating history record for stage: {}, transactionId: {}", stage, transactionId);

        DocumentProcessingHistory history = DocumentProcessingHistory.builder()
                .transactionId(transactionId)
                .stage(stage)
                .status(ProcessingStatus.IN_PROGRESS)
                .s3Uri(s3Uri)
                .requestPayload(toJson(request))
                .build();

        DocumentProcessingHistory saved = historyRepository.save(history);
        log.info("Created history record: id={}, stage={}, transactionId={}",
                saved.getId(), stage, transactionId);

        return saved;
    }

    /**
     * Update history record to SUCCESS
     * 
     * @param historyId        History record ID
     * @param transactionId    Transaction ID (may be null initially, populated from
     *                         response)
     * @param response         Response object
     * @param processingTimeMs Processing duration in milliseconds
     */
    @Transactional
    public void updateSuccess(
            Long historyId,
            String transactionId,
            Object response,
            Long processingTimeMs) {

        log.debug("Updating history {} to SUCCESS, transactionId: {}", historyId, transactionId);

        Optional<DocumentProcessingHistory> historyOpt = historyRepository.findById(historyId);
        if (historyOpt.isEmpty()) {
            log.warn("History record not found: {}", historyId);
            return;
        }

        DocumentProcessingHistory history = historyOpt.get();
        history.setStatus(ProcessingStatus.SUCCESS);
        history.setTransactionId(transactionId);
        history.setResponsePayload(toJson(response));
        history.setProcessingTimeMs(processingTimeMs);

        historyRepository.save(history);
        log.info("Updated history {} to SUCCESS, processingTime={}ms", historyId, processingTimeMs);
    }

    /**
     * Update history record to FAILED
     * 
     * @param historyId    History record ID
     * @param errorMessage Error message
     * @param exception    Exception object for stack trace
     */
    @Transactional
    public void updateFailure(Long historyId, String errorMessage, Exception exception) {
        log.debug("Updating history {} to FAILED", historyId);

        Optional<DocumentProcessingHistory> historyOpt = historyRepository.findById(historyId);
        if (historyOpt.isEmpty()) {
            log.warn("History record not found: {}", historyId);
            return;
        }

        DocumentProcessingHistory history = historyOpt.get();
        history.setStatus(ProcessingStatus.FAILED);
        history.setErrorMessage(errorMessage);
        history.setErrorStackTrace(getStackTrace(exception));

        historyRepository.save(history);
        log.warn("Updated history {} to FAILED: {}", historyId, errorMessage);
    }

    /**
     * Get all history records for a transaction ID
     * 
     * @param transactionId Transaction ID
     * @return List of history records ordered by creation time
     */
    public List<DocumentProcessingHistory> getHistoryByTransactionId(String transactionId) {
        log.debug("Fetching history for transactionId: {}", transactionId);
        return historyRepository.findByTransactionIdOrderByCreatedAtAsc(transactionId);
    }

    /**
     * Get the latest history record for a transaction
     * 
     * @param transactionId Transaction ID
     * @return Optional latest history record
     */
    public Optional<DocumentProcessingHistory> getLatestByTransactionId(String transactionId) {
        log.debug("Fetching latest history for transactionId: {}", transactionId);
        return historyRepository.findLatestByTransactionId(transactionId);
    }

    /**
     * Get history for a specific stage
     * 
     * @param transactionId Transaction ID
     * @param stage         Processing stage
     * @return Optional history record
     */
    public Optional<DocumentProcessingHistory> getHistoryByTransactionIdAndStage(
            String transactionId,
            ProcessingStage stage) {
        log.debug("Fetching history for transactionId: {}, stage: {}", transactionId, stage);
        return historyRepository.findByTransactionIdAndStage(transactionId, stage);
    }

    /**
     * Get all history records by status
     * 
     * @param status Processing status
     * @return List of history records
     */
    public List<DocumentProcessingHistory> getHistoryByStatus(ProcessingStatus status) {
        log.debug("Fetching history by status: {}", status);
        return historyRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    /**
     * Convert object to JSON string
     */
    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize object to JSON: {}", e.getMessage());
            return obj.toString();
        }
    }

    /**
     * Get stack trace from exception as string
     */
    private String getStackTrace(Exception exception) {
        if (exception == null) {
            return null;
        }

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        exception.printStackTrace(pw);
        return sw.toString();
    }
}
