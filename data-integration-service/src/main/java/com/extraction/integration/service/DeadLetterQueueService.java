package com.extraction.integration.service;

import com.extraction.integration.dto.IngestRequestMessage;
import com.extraction.integration.entity.FailedMessage;
import com.extraction.integration.entity.FailedMessageStatus;
import com.extraction.integration.repository.FailedMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Service to consume and process messages from Dead Letter Queue.
 * Persists failed messages for analysis and potential retry.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeadLetterQueueService {

    private final FailedMessageRepository failedMessageRepository;
    private final ObjectMapper objectMapper;

    private static final int MAX_RETRY_ATTEMPTS = 5;

    /**
     * Listen to Dead Letter Queue and persist failed messages
     */
    @RabbitListener(queues = "${messaging.queue.executor-ingest}.dlq")
    @Transactional
    public void handleDeadLetter(
            IngestRequestMessage message,
            @Header(value = "x-death", required = false) List<Map<String, Object>> xDeath) {

        log.warn("Received dead letter message: jobId={}, systemId={}",
                message.getJobId(), message.getSystemId());

        try {
            // Extract failure reason from x-death header
            String reason = extractDeathReason(xDeath);

            // Persist for analysis
            FailedMessage failedMessage = FailedMessage.builder()
                    .jobId(message.getJobId())
                    .systemId(message.getSystemId())
                    .originalQueue("q.executor.ingest")
                    .errorMessage(reason)
                    .messagePayload(objectMapper.writeValueAsString(message))
                    .status(FailedMessageStatus.PENDING)
                    .retryCount(extractRetryCount(xDeath))
                    .build();

            failedMessageRepository.save(failedMessage);

            log.info("Persisted failed message: id={}, jobId={}",
                    failedMessage.getId(), message.getJobId());

        } catch (Exception e) {
            log.error("Failed to process dead letter: {}", e.getMessage(), e);
        }
    }

    /**
     * Get count of pending failed messages
     */
    public long getPendingCount() {
        return failedMessageRepository.countByStatus(FailedMessageStatus.PENDING);
    }

    /**
     * Get failed messages eligible for retry
     */
    public List<FailedMessage> getRetryableMessages() {
        return failedMessageRepository.findByStatusAndRetryCountLessThan(
                FailedMessageStatus.PENDING, MAX_RETRY_ATTEMPTS);
    }

    // ==================== Private Methods ====================

    private String extractDeathReason(List<Map<String, Object>> xDeath) {
        if (xDeath == null || xDeath.isEmpty()) {
            return "Unknown reason";
        }
        Map<String, Object> firstDeath = xDeath.get(0);
        return (String) firstDeath.getOrDefault("reason", "rejected");
    }

    private int extractRetryCount(List<Map<String, Object>> xDeath) {
        if (xDeath == null || xDeath.isEmpty()) {
            return 0;
        }
        Map<String, Object> firstDeath = xDeath.get(0);
        Object count = firstDeath.get("count");
        return count != null ? ((Number) count).intValue() : 1;
    }
}
