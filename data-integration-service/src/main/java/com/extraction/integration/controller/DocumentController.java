package com.extraction.integration.controller;

import com.extraction.integration.config.RabbitMQConfig;
import com.extraction.integration.dto.CheckCompletenessRequest;
import com.extraction.integration.dto.CrossCheckRequest;
import com.extraction.integration.dto.ExtractDataRequest;
import com.extraction.integration.dto.SplitRenameRequest;
import com.extraction.integration.service.MinioStorageService;
import com.extraction.integration.dto.SystemInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final RabbitTemplate rabbitTemplate;
    private final MinioStorageService minioStorageService;

    /**
     * Split & Rename documents from uploaded file(s).
     *
     * Flow per file:
     * - Upload file to MinIO temp bucket
     * - Convert MinIO path to s3_uri format
     * - Send SplitRenameRequest to processing exchange
     */
    @PostMapping(value = "/split-rename", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> splitRename(@RequestParam("file") MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return ResponseEntity.badRequest().body("No file provided");
        }

        // Lấy SystemInfo từ SecurityContext (tương tự IngestController)
        SystemInfo systemInfo = (SystemInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String systemId = systemInfo.getSystemId();

        List<Map<String, Object>> results = new ArrayList<>();

        for (MultipartFile file : files) {
            Map<String, Object> fileResult = new HashMap<>();
            fileResult.put("fileName", file.getOriginalFilename());
            try {
                // Upload từng file lên MinIO (temp bucket)
                String minioPath = minioStorageService.uploadToTemp(file, systemId);
                String s3Uri = convertToS3Uri(minioPath);
                fileResult.put("s3_uri", s3Uri);

                // Gửi message vào hàng đợi Split & Rename
                SplitRenameRequest request = new SplitRenameRequest();
                request.setS3_uri(s3Uri);

                Object response = rabbitTemplate.convertSendAndReceive(
                        RabbitMQConfig.PROCESSING_EXCHANGE,
                        RabbitMQConfig.ROUTING_KEY_SPLIT,
                        request);

                fileResult.put("queueResponse", response);
                results.add(fileResult);
            } catch (RuntimeException ex) {
                log.error("Service error during split-rename for file {}: {}", file.getOriginalFilename(), ex.getMessage(), ex);
                fileResult.put("error", ex.getMessage());
                results.add(fileResult);
            } catch (Exception ex) {
                log.error("Failed to process split-rename for file {}: {}", file.getOriginalFilename(), ex.getMessage(), ex);
                fileResult.put("error", "Failed to process file: " + ex.getMessage());
                results.add(fileResult);
            }
        }

        return ResponseEntity.ok(results);
    }

    private String convertToS3Uri(String minioPath) {
        if (minioPath == null || minioPath.isEmpty()) {
            throw new IllegalArgumentException("minioPath cannot be null or empty");
        }
        return "s3://" + minioPath;
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
