package com.extraction.executor.listener;

import com.extraction.executor.client.EtlClient;
import com.extraction.executor.dto.CheckCompletenessRequest;
import com.extraction.executor.dto.CrossCheckRequest;
import com.extraction.executor.dto.ExtractDataRequest;
import com.extraction.executor.dto.IngestRequestMessage;
import com.extraction.executor.dto.SplitRenameRequest;
import com.extraction.executor.entity.DocumentProcessingHistory;
import com.extraction.executor.entity.ProcessingStage;
import com.extraction.executor.entity.ProcessingStatus;
import com.extraction.executor.repository.DocumentProcessingHistoryRepository;
import com.extraction.executor.service.MinioStorageService;
import com.extraction.executor.service.SftpService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.ChannelSftp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
@RequiredArgsConstructor
public class IngestRequestListener {

    private final MinioStorageService minioStorageService;
    private final SftpService sftpService;
    private final EtlClient etlClient;
    private final DocumentProcessingHistoryRepository historyRepository;
    private final ObjectMapper objectMapper;

    @Value("${sftp.default.host}")
    private String defaultSftpHost;

    @Value("${sftp.default.port}")
    private int defaultSftpPort;

    @Value("${sftp.default.username}")
    private String defaultSftpUsername;

    @Value("${sftp.default.password}")
    private String defaultSftpPassword;

    @Value("${sftp.default.remote-directory}")
    private String defaultRemoteDir;

    // Virtual Thread executor for parallel downloads
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @RabbitListener(queues = "${messaging.queue.executor-ingest}")
    public void handleIngestRequest(IngestRequestMessage message) {
        log.info("Received ingest request: jobId={}, type={}, systemId={}",
                message.getJobId(), message.getType(), message.getSystemId());

        try {
            if (message.getType() == IngestRequestMessage.RequestType.SYNC) {
                handleSyncRequest(message);
            } else {
                log.warn("Unsupported request type: {}. Only SYNC is supported.", message.getType());
            }
        } catch (Exception e) {
            log.error("Error processing ingest request: jobId={}, error={}",
                    message.getJobId(), e.getMessage(), e);
            // Message will be retried or sent to DLQ based on RabbitMQ configuration
        }
    }

    /**
     * Handle sync request - download files from SFTP and process them
     */
    private void handleSyncRequest(IngestRequestMessage message) throws Exception {
        log.info("Processing sync request: jobId={}", message.getJobId());

        // Parse source config
        JsonNode config = objectMapper.readTree(message.getSourceConfig());
        String sourceType = config.has("sourceType") ? config.get("sourceType").asText() : "SFTP";

        if ("SFTP".equalsIgnoreCase(sourceType)) {
            handleSftpSync(message, config);
        } else {
            log.warn("Unsupported source type: {}", sourceType);
            throw new IllegalArgumentException("Unsupported source type: " + sourceType);
        }
    }

    /**
     * Download files from SFTP in parallel using Virtual Threads and process each file
     */
    private void handleSftpSync(IngestRequestMessage message, JsonNode config) throws Exception {
        String host = config.has("host") ? config.get("host").asText() : defaultSftpHost;
        int port = config.has("port") ? config.get("port").asInt() : defaultSftpPort;
        String username = config.has("username") ? config.get("username").asText() : defaultSftpUsername;
        String password = config.has("password") ? config.get("password").asText() : defaultSftpPassword;
        String remoteDir = config.has("remotePath") ? config.get("remotePath").asText() : defaultRemoteDir;
        String pattern = config.has("filePattern") ? config.get("filePattern").asText() : null;

        ChannelSftp channel = null;
        try {
            log.info("Connecting to SFTP: host={}, port={}, remoteDir={}, pattern={}", 
                    host, port, remoteDir, pattern);
            channel = sftpService.connect(host, port, username, password);
            List<String> files = sftpService.listFiles(channel, remoteDir, pattern);

            log.info("Found {} files to download and process for job: {}", files.size(), message.getJobId());

            // Download and process files in parallel using Virtual Threads
            final ChannelSftp finalChannel = channel;
            for (String filePath : files) {
                virtualExecutor.submit(() -> downloadAndProcessFile(finalChannel, filePath, message));
            }

        } finally {
            // Note: In production, manage connection lifecycle better
            // For now, leave connection open for parallel downloads
        }
    }

    /**
     * Download file from SFTP, upload to MinIO, and process through all stages
     */
    private void downloadAndProcessFile(ChannelSftp channel, String remotePath, IngestRequestMessage message) {
        String fileName = null;
        String minioPath = null;
        String s3Uri = null;
        
        try {
            fileName = remotePath.substring(remotePath.lastIndexOf('/') + 1);
            log.info("Starting download and processing: jobId={}, file={}", message.getJobId(), fileName);
            
            long fileSize = sftpService.getFileSize(channel, remotePath);
            log.debug("File size: {} bytes for file: {}", fileSize, fileName);

            try (InputStream inputStream = sftpService.downloadFile(channel, remotePath)) {
                // Upload to MinIO
                log.info("Uploading to MinIO: jobId={}, file={}", message.getJobId(), fileName);
                minioPath = minioStorageService.uploadToRaw(
                        inputStream, fileName, message.getSystemId(), fileSize, "application/octet-stream");
                
                // Convert minioPath to s3_uri format (s3://bucket/path)
                s3Uri = convertToS3Uri(minioPath);
                log.info("File uploaded to MinIO: jobId={}, s3Uri={}", message.getJobId(), s3Uri);

                // Process file through all stages
                processFileThroughStages(message.getJobId(), s3Uri, fileName);
                
                log.info("Completed processing: jobId={}, file={}", message.getJobId(), fileName);
            }
        } catch (Exception e) {
            log.error("Error processing file: jobId={}, file={}, error={}", 
                    message.getJobId(), fileName, e.getMessage(), e);
            // Log error but don't throw - let other files continue processing
        }
    }

    /**
     * Process file through all document processing stages:
     * 1. Split & Rename
     * 2. Check Completeness
     * 3. Extract Data
     * 4. Cross Check
     */
    private void processFileThroughStages(String jobId, String s3Uri, String fileName) {
        String transactionId = null;
        
        try {
            // Stage 1: Split & Rename
            log.info("=== STAGE 1: SPLIT_RENAME - jobId={}, s3Uri={} ===", jobId, s3Uri);
            transactionId = processSplitRename(jobId, s3Uri, fileName);
            
            if (transactionId == null) {
                log.error("SPLIT_RENAME failed - cannot proceed. jobId={}, s3Uri={}", jobId, s3Uri);
                return;
            }
            
            log.info("SPLIT_RENAME completed successfully. jobId={}, transactionId={}", jobId, transactionId);
            
            // Stage 2: Check Completeness
            log.info("=== STAGE 2: CHECK_COMPLETENESS - jobId={}, transactionId={} ===", jobId, transactionId);
            boolean completenessSuccess = processCheckCompleteness(jobId, transactionId);
            
            if (!completenessSuccess) {
                log.error("CHECK_COMPLETENESS failed - cannot proceed. jobId={}, transactionId={}", jobId, transactionId);
                return;
            }
            
            log.info("CHECK_COMPLETENESS completed successfully. jobId={}, transactionId={}", jobId, transactionId);
            
            // Stage 3: Extract Data
            log.info("=== STAGE 3: EXTRACT_DATA - jobId={}, transactionId={} ===", jobId, transactionId);
            boolean extractSuccess = processExtractData(jobId, transactionId);
            
            if (!extractSuccess) {
                log.error("EXTRACT_DATA failed - cannot proceed. jobId={}, transactionId={}", jobId, transactionId);
                return;
            }
            
            log.info("EXTRACT_DATA completed successfully. jobId={}, transactionId={}", jobId, transactionId);
            
            // Stage 4: Cross Check
            log.info("=== STAGE 4: CROSS_CHECK - jobId={}, transactionId={} ===", jobId, transactionId);
            boolean crossCheckSuccess = processCrossCheck(jobId, transactionId);
            
            if (crossCheckSuccess) {
                log.info("All stages completed successfully. jobId={}, transactionId={}", jobId, transactionId);
            } else {
                log.error("CROSS_CHECK failed. jobId={}, transactionId={}", jobId, transactionId);
            }
            
        } catch (Exception e) {
            log.error("Unexpected error in processFileThroughStages: jobId={}, transactionId={}, error={}", 
                    jobId, transactionId, e.getMessage(), e);
        }
    }

    /**
     * Stage 1: Split & Rename
     */
    private String processSplitRename(String jobId, String s3Uri, String fileName) {
        long startTime = System.currentTimeMillis();
        ProcessingStage stage = ProcessingStage.SPLIT_RENAME;
        String transactionId = null;
        Map<String, Object> result = null;
        Map<String, Object> requestPayload = null;
        String errorMsg = null;
        String errorStackTrace = null;

        try {
            log.info("Starting SPLIT_RENAME: jobId={}, s3Uri={}, fileName={}", jobId, s3Uri, fileName);
            
            // Prepare request
            SplitRenameRequest request = new SplitRenameRequest();
            request.setS3_uri(s3Uri);
            
            requestPayload = new HashMap<>();
            requestPayload.put("s3_uri", s3Uri);
            
            log.debug("SPLIT_RENAME request: {}", objectMapper.writeValueAsString(requestPayload));
            
            // Call ETL API
            result = etlClient.splitRename(requestPayload);
            
            // Extract transaction_id from response
            if (result != null && result.containsKey("transaction_id")) {
                transactionId = (String) result.get("transaction_id");
                log.info("SPLIT_RENAME successful: jobId={}, transactionId={}, duration={}ms", 
                        jobId, transactionId, System.currentTimeMillis() - startTime);
            } else {
                errorMsg = "Response does not contain transaction_id";
                log.error("SPLIT_RENAME failed - no transaction_id: jobId={}, response={}", jobId, result);
            }
            
        } catch (Exception e) {
            errorMsg = e.getMessage();
            errorStackTrace = getStackTrace(e);
            log.error("SPLIT_RENAME exception: jobId={}, s3Uri={}, error={}, duration={}ms", 
                    jobId, s3Uri, errorMsg, System.currentTimeMillis() - startTime, e);
        } finally {
            // Save history
            saveHistory(transactionId, stage, 
                    transactionId != null ? ProcessingStatus.SUCCESS : ProcessingStatus.FAILED,
                    s3Uri, requestPayload, result, errorMsg, errorStackTrace,
                    System.currentTimeMillis() - startTime);
        }
        
        return transactionId;
    }

    /**
     * Stage 2: Check Completeness
     */
    private boolean processCheckCompleteness(String jobId, String transactionId) {
        long startTime = System.currentTimeMillis();
        ProcessingStage stage = ProcessingStage.CHECK_COMPLETENESS;
        Map<String, Object> result = null;
        Map<String, Object> requestPayload = null;
        String errorMsg = null;
        String errorStackTrace = null;
        boolean success = false;

        try {
            log.info("Starting CHECK_COMPLETENESS: jobId={}, transactionId={}", jobId, transactionId);
            
            // Prepare request
            CheckCompletenessRequest request = new CheckCompletenessRequest();
            request.setTransaction_id(transactionId);
            
            requestPayload = new HashMap<>();
            requestPayload.put("transaction_id", transactionId);
            
            log.debug("CHECK_COMPLETENESS request: {}", objectMapper.writeValueAsString(requestPayload));
            
            // Call ETL API
            result = etlClient.checkCompleteness(requestPayload);
            
            success = true;
            log.info("CHECK_COMPLETENESS successful: jobId={}, transactionId={}, duration={}ms", 
                    jobId, transactionId, System.currentTimeMillis() - startTime);
            
        } catch (Exception e) {
            errorMsg = e.getMessage();
            errorStackTrace = getStackTrace(e);
            log.error("CHECK_COMPLETENESS exception: jobId={}, transactionId={}, error={}, duration={}ms", 
                    jobId, transactionId, errorMsg, System.currentTimeMillis() - startTime, e);
        } finally {
            // Save history
            saveHistory(transactionId, stage,
                    success ? ProcessingStatus.SUCCESS : ProcessingStatus.FAILED,
                    null, requestPayload, result, errorMsg, errorStackTrace,
                    System.currentTimeMillis() - startTime);
        }
        
        return success;
    }

    /**
     * Stage 3: Extract Data
     */
    private boolean processExtractData(String jobId, String transactionId) {
        long startTime = System.currentTimeMillis();
        ProcessingStage stage = ProcessingStage.EXTRACT_DATA;
        Map<String, Object> result = null;
        Map<String, Object> requestPayload = null;
        String errorMsg = null;
        String errorStackTrace = null;
        boolean success = false;

        try {
            log.info("Starting EXTRACT_DATA: jobId={}, transactionId={}", jobId, transactionId);
            
            // Prepare request
            ExtractDataRequest request = new ExtractDataRequest();
            request.setTransaction_id(transactionId);
            
            requestPayload = new HashMap<>();
            requestPayload.put("transaction_id", transactionId);
            
            log.debug("EXTRACT_DATA request: {}", objectMapper.writeValueAsString(requestPayload));
            
            // Call ETL API
            result = etlClient.extractData(requestPayload);
            
            success = true;
            log.info("EXTRACT_DATA successful: jobId={}, transactionId={}, duration={}ms", 
                    jobId, transactionId, System.currentTimeMillis() - startTime);
            
        } catch (Exception e) {
            errorMsg = e.getMessage();
            errorStackTrace = getStackTrace(e);
            log.error("EXTRACT_DATA exception: jobId={}, transactionId={}, error={}, duration={}ms", 
                    jobId, transactionId, errorMsg, System.currentTimeMillis() - startTime, e);
        } finally {
            // Save history
            saveHistory(transactionId, stage,
                    success ? ProcessingStatus.SUCCESS : ProcessingStatus.FAILED,
                    null, requestPayload, result, errorMsg, errorStackTrace,
                    System.currentTimeMillis() - startTime);
        }
        
        return success;
    }

    /**
     * Stage 4: Cross Check
     */
    private boolean processCrossCheck(String jobId, String transactionId) {
        long startTime = System.currentTimeMillis();
        ProcessingStage stage = ProcessingStage.CROSS_CHECK;
        Map<String, Object> result = null;
        Map<String, Object> requestPayload = null;
        String errorMsg = null;
        String errorStackTrace = null;
        boolean success = false;

        try {
            log.info("Starting CROSS_CHECK: jobId={}, transactionId={}", jobId, transactionId);
            
            // Prepare request
            CrossCheckRequest request = new CrossCheckRequest();
            request.setTransaction_id(transactionId);
            
            requestPayload = new HashMap<>();
            requestPayload.put("transaction_id", transactionId);
            
            log.debug("CROSS_CHECK request: {}", objectMapper.writeValueAsString(requestPayload));
            
            // Call ETL API
            result = etlClient.crossCheck(requestPayload);
            
            success = true;
            log.info("CROSS_CHECK successful: jobId={}, transactionId={}, duration={}ms", 
                    jobId, transactionId, System.currentTimeMillis() - startTime);
            
        } catch (Exception e) {
            errorMsg = e.getMessage();
            errorStackTrace = getStackTrace(e);
            log.error("CROSS_CHECK exception: jobId={}, transactionId={}, error={}, duration={}ms", 
                    jobId, transactionId, errorMsg, System.currentTimeMillis() - startTime, e);
        } finally {
            // Save history
            saveHistory(transactionId, stage,
                    success ? ProcessingStatus.SUCCESS : ProcessingStatus.FAILED,
                    null, requestPayload, result, errorMsg, errorStackTrace,
                    System.currentTimeMillis() - startTime);
        }
        
        return success;
    }

    /**
     * Save processing history to database
     */
    private void saveHistory(String transactionId, ProcessingStage stage, ProcessingStatus status,
                            String s3Uri, Object requestPayload, Object responsePayload,
                            String errorMsg, String errorStackTrace, long duration) {
        try {
            String requestJson = requestPayload != null ? objectMapper.writeValueAsString(requestPayload) : null;
            String responseJson = responsePayload != null ? objectMapper.writeValueAsString(responsePayload) : null;
            
            DocumentProcessingHistory history = DocumentProcessingHistory.builder()
                    .transactionId(transactionId)
                    .stage(stage)
                    .status(status)
                    .s3Uri(s3Uri)
                    .requestPayload(requestJson)
                    .responsePayload(responseJson)
                    .errorMessage(errorMsg)
                    .errorStackTrace(errorStackTrace)
                    .processingTimeMs(duration)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            
            historyRepository.save(history);
            log.debug("Saved history: transactionId={}, stage={}, status={}, duration={}ms", 
                    transactionId, stage, status, duration);
            
        } catch (Exception e) {
            log.error("Failed to save history: transactionId={}, stage={}, error={}", 
                    transactionId, stage, e.getMessage(), e);
        }
    }

    /**
     * Convert minioPath (bucket/path) to s3_uri format (s3://bucket/path)
     */
    private String convertToS3Uri(String minioPath) {
        if (minioPath == null || minioPath.isEmpty()) {
            throw new IllegalArgumentException("minioPath cannot be null or empty");
        }
        return "s3://" + minioPath;
    }

    /**
     * Get stack trace as string
     */
    private String getStackTrace(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
}
