package com.extraction.executor.listener;

import com.extraction.executor.client.DocumentProcessingClient;
import com.extraction.executor.config.RabbitMQConfig;
import com.extraction.executor.dto.CheckCompletenessRequest;
import com.extraction.executor.dto.CrossCheckRequest;
import com.extraction.executor.dto.ExtractDataRequest;
import com.extraction.executor.dto.SplitRenameRequest;
import com.extraction.executor.dto.document.*;
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

    private final DocumentProcessingClient documentProcessingClient;
    private final DocumentProcessingHistoryRepository historyRepository;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_SPLIT)
    public Map<String, Object> handleSplitRename(com.extraction.executor.dto.SplitRenameRequest request) {
        log.info("Processing SPLIT_RENAME for URI: {}", request.getS3_uri());

        long startTime = System.currentTimeMillis();
        SplitRenameResponse response = null;
        Map<String, Object> result = null;
        ProcessingStatus status = ProcessingStatus.FAILED;
        String transactionId = null;
        String errorMsg = null;

        try {
            // Convert to DTO from document package
            SplitRenameRequest dtoRequest = SplitRenameRequest.builder()
                    .s3Uri(request.getS3_uri())
                    .build();
            
            // Call ETL API using type-safe client
            response = documentProcessingClient.splitAndRename(dtoRequest);
            transactionId = response.getTransactionId();
            status = ProcessingStatus.SUCCESS;
            
            // Convert response to Map for RabbitMQ RPC
            @SuppressWarnings("unchecked")
            Map<String, Object> convertedResult = (Map<String, Object>) objectMapper.convertValue(response, Map.class);
            result = convertedResult;
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

        long startTime = System.currentTimeMillis();
        CheckCompletenessResponse response = null;
        Map<String, Object> result = null;
        ProcessingStatus status = ProcessingStatus.FAILED;
        String errorMsg = null;

        try {
            // Convert to DTO
            com.extraction.executor.dto.document.CheckCompletenessRequest dtoRequest = 
                    com.extraction.executor.dto.document.CheckCompletenessRequest.builder()
                    .transactionId(request.getTransaction_id())
                    .build();
            
            // Call ETL API using type-safe client
            response = documentProcessingClient.checkCompleteness(dtoRequest);
            status = ProcessingStatus.SUCCESS;
            
            // Convert response to Map for RabbitMQ RPC
            @SuppressWarnings("unchecked")
            Map<String, Object> convertedResult = (Map<String, Object>) objectMapper.convertValue(response, Map.class);
            result = convertedResult;
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

        long startTime = System.currentTimeMillis();
        ExtractDataResponse response = null;
        Map<String, Object> result = null;
        ProcessingStatus status = ProcessingStatus.FAILED;
        String errorMsg = null;

        try {
            // Convert to DTO
            com.extraction.executor.dto.document.ExtractDataRequest dtoRequest = 
                    com.extraction.executor.dto.document.ExtractDataRequest.builder()
                    .transactionId(request.getTransaction_id())
                    .build();
            
            // Call ETL API using type-safe client
            response = documentProcessingClient.extractData(dtoRequest);
            status = ProcessingStatus.SUCCESS;
            
            // Convert response to Map for RabbitMQ RPC
            @SuppressWarnings("unchecked")
            Map<String, Object> convertedResult = (Map<String, Object>) objectMapper.convertValue(response, Map.class);
            result = convertedResult;
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

        long startTime = System.currentTimeMillis();
        CrossCheckResponse response = null;
        Map<String, Object> result = null;
        ProcessingStatus status = ProcessingStatus.FAILED;
        String errorMsg = null;

        try {
            // Convert to DTO
            com.extraction.executor.dto.document.CrossCheckRequest dtoRequest = 
                    com.extraction.executor.dto.document.CrossCheckRequest.builder()
                    .transactionId(request.getTransaction_id())
                    .build();
            
            // Call ETL API using type-safe client
            response = documentProcessingClient.crossCheck(dtoRequest);
            status = ProcessingStatus.SUCCESS;
            
            // Convert response to Map for RabbitMQ RPC
            @SuppressWarnings("unchecked")
            Map<String, Object> convertedResult = (Map<String, Object>) objectMapper.convertValue(response, Map.class);
            result = convertedResult;
        } catch (Exception e) {
            log.error("Error in CROSS_CHECK", e);
            errorMsg = e.getMessage();
        } finally {
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
