package com.extraction.integration.service;

import com.extraction.integration.dto.IngestRequestMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Service to consume messages from Dead Letter Queue.
 * Stateless implementation: Logs failed messages for monitoring.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeadLetterQueueService {

    private final ObjectMapper objectMapper;

    /**
     * Listen to Dead Letter Queue and log failed messages
     */
    @RabbitListener(queues = "${messaging.queue.executor-ingest}.dlq")
    public void handleDeadLetter(
            IngestRequestMessage message,
            @Header(value = "x-death", required = false) List<Map<String, Object>> xDeath) {

        log.warn("Received dead letter message: jobId={}, systemId={}",
                message.getJobId(), message.getSystemId());

        try {
            // Extract failure reason from x-death header
            String reason = extractDeathReason(xDeath);
            int retryCount = extractRetryCount(xDeath);
            String payload = objectMapper.writeValueAsString(message);

            // Log detailed failure info for monitoring systems (Prometheus/Grafana/ELK)
            log.error("DLQ Processing - JobID: {}, SystemID: {}, Reason: {}, RetryCount: {}, Payload: {}", 
                    message.getJobId(), message.getSystemId(), reason, retryCount, payload);

        } catch (Exception e) {
            log.error("Failed to process dead letter: {}", e.getMessage(), e);
        }
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
