package com.extraction.integration.controller;

import com.extraction.integration.config.RabbitMQConfig;
import com.extraction.integration.dto.CheckCompletenessRequest;
import com.extraction.integration.dto.CrossCheckRequest;
import com.extraction.integration.dto.ExtractDataRequest;
import com.extraction.integration.dto.SplitRenameRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final RabbitTemplate rabbitTemplate;

    @PostMapping("/split-rename")
    public ResponseEntity<?> splitRename(@RequestBody SplitRenameRequest request) {
        log.info("Received Split & Rename request for S3 URI: {}", request.getS3_uri());
        // Auto-generate transaction ID if not present (logic specific to POC flow)
        // Here we expect the executor to maybe assign it or we assign it.
        // Example response has transaction_id.
        // We act as RPC client.
        Object response = rabbitTemplate.convertSendAndReceive(
                RabbitMQConfig.PROCESSING_EXCHANGE,
                RabbitMQConfig.ROUTING_KEY_SPLIT,
                request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/check-completeness")
    public ResponseEntity<?> checkCompleteness(@RequestBody CheckCompletenessRequest request) {
        log.info("Received Check Completeness request for Transaction ID: {}", request.getTransaction_id());
        Object response = rabbitTemplate.convertSendAndReceive(
                RabbitMQConfig.PROCESSING_EXCHANGE,
                RabbitMQConfig.ROUTING_KEY_CHECK,
                request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/extract-data")
    public ResponseEntity<?> extractData(@RequestBody ExtractDataRequest request) {
        log.info("Received Extract Data request for Transaction ID: {}", request.getTransaction_id());
        Object response = rabbitTemplate.convertSendAndReceive(
                RabbitMQConfig.PROCESSING_EXCHANGE,
                RabbitMQConfig.ROUTING_KEY_EXTRACT,
                request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/cross-check")
    public ResponseEntity<?> crossCheck(@RequestBody CrossCheckRequest request) {
        log.info("Received Cross Check request for Transaction ID: {}", request.getTransaction_id());
        Object response = rabbitTemplate.convertSendAndReceive(
                RabbitMQConfig.PROCESSING_EXCHANGE,
                RabbitMQConfig.ROUTING_KEY_CROSSCHECK,
                request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history/{transactionId}")
    public ResponseEntity<?> getHistory(@PathVariable String transactionId) {
        log.info("Received History request for Transaction ID: {}", transactionId);
        // Send string as message or a wrapper object
        Object response = rabbitTemplate.convertSendAndReceive(
                RabbitMQConfig.HISTORY_EXCHANGE,
                RabbitMQConfig.ROUTING_KEY_HISTORY_REQUEST,
                transactionId);
        return ResponseEntity.ok(response);
    }
}
