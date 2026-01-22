package com.extraction.executor.listener;

import com.extraction.executor.client.EtlClient;
import com.extraction.executor.config.RabbitMQConfig;
import com.extraction.executor.dto.CheckCompletenessRequest;
import com.extraction.executor.dto.CrossCheckRequest;
import com.extraction.executor.dto.ExtractDataRequest;
import com.extraction.executor.dto.SplitRenameRequest;
import com.extraction.executor.entity.DocumentProcessingHistory;
import com.extraction.executor.entity.ProcessingStage;
import com.extraction.executor.entity.ProcessingStatus;
import com.extraction.executor.repository.DocumentProcessingHistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessingListener {

    private final EtlClient etlClient;
    private final DocumentProcessingHistoryRepository historyRepository;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_SPLIT)
    public Map<String, Object> handleSplitRename(SplitRenameRequest request) {
        log.info("Processing SPLIT_RENAME for URI: {}", request.getS3_uri());
        Map<String, Object> payload = objectMapper.convertValue(request, Map.class);

        long startTime = System.currentTimeMillis();
        Map<String, Object> result = null;
        ProcessingStatus status = ProcessingStatus.FAILED;
        String transactionId = null;
        String errorMsg = null;

        try {
            result = etlClient.splitRename(payload);
            transactionId = (String) result.get("transaction_id");
            status = ProcessingStatus.SUCCESS;
        } catch (Exception e) {
            log.error("Error in SPLIT_RENAME", e);
            errorMsg = e.getMessage();
        } finally {
            saveHistory(transactionId, ProcessingStage.SPLIT_RENAME, status, request, result, errorMsg,
                    System.currentTimeMillis() - startTime);
        }
        return result;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_CHECK)
    public Map<String, Object> handleCheckCompleteness(CheckCompletenessRequest request) {
        log.info("Processing CHECK_COMPLETENESS for TRX: {}", request.getTransaction_id());
        Map<String, Object> payload = objectMapper.convertValue(request, Map.class);

        long startTime = System.currentTimeMillis();
        Map<String, Object> result = null;
        ProcessingStatus status = ProcessingStatus.FAILED;
        String errorMsg = null;

        try {
            result = etlClient.checkCompleteness(payload);
            status = ProcessingStatus.SUCCESS;
        } catch (Exception e) {
            log.error("Error in CHECK_COMPLETENESS", e);
            errorMsg = e.getMessage();
        } finally {
            saveHistory(request.getTransaction_id(), ProcessingStage.CHECK_COMPLETENESS, status, request, result,
                    errorMsg, System.currentTimeMillis() - startTime);
        }
        return result;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_EXTRACT)
    public Map<String, Object> handleExtractData(ExtractDataRequest request) {
        log.info("Processing EXTRACT_DATA for TRX: {}", request.getTransaction_id());
        Map<String, Object> payload = objectMapper.convertValue(request, Map.class);

        long startTime = System.currentTimeMillis();
        Map<String, Object> result = null;
        ProcessingStatus status = ProcessingStatus.FAILED;
        String errorMsg = null;

        try {
            result = etlClient.extractData(payload);
            status = ProcessingStatus.SUCCESS;
        } catch (Exception e) {
            log.error("Error in EXTRACT_DATA", e);
            errorMsg = e.getMessage();
        } finally {
            saveHistory(request.getTransaction_id(), ProcessingStage.EXTRACT_DATA, status, request, result, errorMsg,
                    System.currentTimeMillis() - startTime);
        }
        return result;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_CROSSCHECK)
    public Map<String, Object> handleCrossCheck(CrossCheckRequest request) {
        log.info("Processing CROSS_CHECK for TRX: {}", request.getTransaction_id());
        Map<String, Object> payload = objectMapper.convertValue(request, Map.class);

        long startTime = System.currentTimeMillis();
        Map<String, Object> result = null;
        ProcessingStatus status = ProcessingStatus.FAILED;
        String errorMsg = null;

        try {
            result = etlClient.crossCheck(payload);
            status = ProcessingStatus.SUCCESS;
        } catch (Exception e) {
            log.error("Error in CROSS_CHECK", e);
            errorMsg = e.getMessage();
        } finally {
            // Need to add CROSS_CHECK to ProcessingStage enum if not exists, assume exists
            // or map to closest
            // Wait, I should check ProcessingStage enum first!
            saveHistory(request.getTransaction_id(), ProcessingStage.CROSS_CHECK, status, request, result, errorMsg,
                    System.currentTimeMillis() - startTime);
        }
        return result;
    }

    private void saveHistory(String transactionId, ProcessingStage stage, ProcessingStatus status, Object request,
            Object response, String errorMsg, long duration) {
        try {
            DocumentProcessingHistory history = DocumentProcessingHistory.builder()
                    .transactionId(transactionId)
                    .stage(stage)
                    .status(status)
                    .requestPayload(objectMapper.writeValueAsString(request))
                    .responsePayload(objectMapper.writeValueAsString(response))
                    .errorMessage(errorMsg)
                    .processingTimeMs(duration)
                    .build();
            historyRepository.save(history);
        } catch (Exception e) {
            log.error("Failed to save history", e);
        }
    }
}
