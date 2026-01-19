package com.extraction.integration.service;

import com.extraction.integration.dto.IngestRequestMessage;
import com.extraction.integration.entity.Job;
import com.extraction.integration.entity.JobType;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessagePublisherService {

    private final RabbitTemplate rabbitTemplate;
    private final JobService jobService;

    @Value("${messaging.exchange.integration}")
    private String exchangeName;

    @Value("${messaging.routing-key.ingest-request}")
    private String routingKey;

    /**
     * Publish an ingest request message to RabbitMQ with Circuit Breaker protection
     * Also creates a Job record for tracking.
     */
    @CircuitBreaker(name = "rabbitService", fallbackMethod = "publishFallback")
    public String publishIngestRequest(IngestRequestMessage message) {
        // Generate job ID if not set
        if (message.getJobId() == null) {
            message.setJobId(UUID.randomUUID().toString());
        }
        if (message.getCreatedAt() == null) {
            message.setCreatedAt(Instant.now());
        }

        log.info("Publishing ingest request: jobId={}, type={}, systemId={}",
                message.getJobId(), message.getType(), message.getSystemId());

        try {
            // Create Job record for tracking
            JobType jobType = message.getType() == IngestRequestMessage.RequestType.UPLOAD
                    ? JobType.UPLOAD
                    : JobType.SYNC;

            Job job = jobService.createJob(
                    message.getRequestId(),
                    message.getSystemId(),
                    jobType,
                    message.getSourcePath());

            // Update message with persisted job ID
            message.setJobId(job.getId().toString());

            // Create correlation data for publisher confirms
            CorrelationData correlationData = new CorrelationData(message.getJobId());

            // Publish with correlation data
            rabbitTemplate.convertAndSend(exchangeName, routingKey, message, correlationData);

            // Wait for confirm with timeout (optional - for strict backpressure)
            CompletableFuture<CorrelationData.Confirm> future = correlationData.getFuture();
            CorrelationData.Confirm confirm = future.get(5, TimeUnit.SECONDS);

            if (confirm != null && confirm.isAck()) {
                log.debug("Message published and confirmed: jobId={}", message.getJobId());
                return message.getJobId();
            } else {
                String reason = confirm != null ? confirm.getReason() : "Unknown";
                log.error("Message not acknowledged: jobId={}, reason={}", message.getJobId(), reason);
                // Mark job as failed
                jobService.failJob(job.getId(), "Message not acknowledged by broker: " + reason);
                throw new RuntimeException("Message not acknowledged by broker: " + reason);
            }

        } catch (Exception e) {
            log.error("Failed to publish message: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to publish message to queue", e);
        }
    }

    /**
     * Fallback method when Circuit Breaker is open
     */
    public String publishFallback(IngestRequestMessage message, Throwable throwable) {
        log.error("Circuit breaker open for RabbitMQ, fallback triggered: jobId={}, error={}",
                message.getJobId(), throwable.getMessage());

        // Option 1: Throw exception to reject the request
        throw new RuntimeException("Service temporarily unavailable. Please retry later.");

        // Option 2: Queue to a local fallback (e.g., Redis, file) - implement if needed
        // return fallbackQueueService.queue(message);
    }
}
