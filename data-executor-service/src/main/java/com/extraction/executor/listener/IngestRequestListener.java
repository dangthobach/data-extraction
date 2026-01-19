package com.extraction.executor.listener;

import com.extraction.executor.dto.FileReadyEvent;
import com.extraction.executor.dto.IngestRequestMessage;
import com.extraction.executor.service.KafkaProducerService;
import com.extraction.executor.service.MinioStorageService;
import com.extraction.executor.service.SftpService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.ChannelSftp;
import io.minio.StatObjectResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
@RequiredArgsConstructor
public class IngestRequestListener {

    private final MinioStorageService minioStorageService;
    private final SftpService sftpService;
    private final KafkaProducerService kafkaProducerService;
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
            switch (message.getType()) {
                case UPLOAD -> handleUploadRequest(message);
                case SYNC -> handleSyncRequest(message);
                default -> log.warn("Unknown request type: {}", message.getType());
            }
        } catch (Exception e) {
            log.error("Error processing ingest request: jobId={}, error={}",
                    message.getJobId(), e.getMessage(), e);
            // TODO: Send to DLQ or error topic
        }
    }

    /**
     * Handle file upload request - move from temp to raw bucket
     */
    private void handleUploadRequest(IngestRequestMessage message) throws Exception {
        log.info("Processing upload request: jobId={}, path={}", message.getJobId(), message.getSourcePath());

        // Move file from temp to raw bucket
        String rawPath = minioStorageService.moveToRawBucket(message.getSourcePath());

        // Get file info
        StatObjectResponse stat = minioStorageService.getObjectInfo(rawPath);

        // Publish file ready event to Kafka
        FileReadyEvent event = FileReadyEvent.builder()
                .jobId(message.getJobId())
                .fileId(UUID.randomUUID().toString())
                .systemId(message.getSystemId())
                .fileName(extractFileName(rawPath))
                .minioPath(rawPath)
                .bucket(minioStorageService.getRawBucket())
                .fileSize(stat.size())
                .contentType(stat.contentType())
                .createdAt(Instant.now())
                .build();

        kafkaProducerService.publishFileReady(event);
        log.info("Completed upload processing: jobId={}, fileId={}", message.getJobId(), event.getFileId());
    }

    /**
     * Handle sync request - download files from SFTP/S3
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
        }
    }

    /**
     * Download files from SFTP in parallel using Virtual Threads
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
            channel = sftpService.connect(host, port, username, password);
            List<String> files = sftpService.listFiles(channel, remoteDir, pattern);

            log.info("Found {} files to download for job: {}", files.size(), message.getJobId());

            // Download files in parallel using Virtual Threads
            final ChannelSftp finalChannel = channel;
            for (String filePath : files) {
                virtualExecutor.submit(() -> downloadAndPublish(finalChannel, filePath, message));
            }

        } finally {
            // Note: In production, manage connection lifecycle better
            // For now, leave connection open for parallel downloads
        }
    }

    private void downloadAndPublish(ChannelSftp channel, String remotePath, IngestRequestMessage message) {
        try {
            String fileName = remotePath.substring(remotePath.lastIndexOf('/') + 1);
            long fileSize = sftpService.getFileSize(channel, remotePath);

            try (InputStream inputStream = sftpService.downloadFile(channel, remotePath)) {
                // Upload to MinIO
                String minioPath = minioStorageService.uploadToRaw(
                        inputStream, fileName, message.getSystemId(), fileSize, "application/octet-stream");

                // Publish file ready event
                FileReadyEvent event = FileReadyEvent.builder()
                        .jobId(message.getJobId())
                        .fileId(UUID.randomUUID().toString())
                        .systemId(message.getSystemId())
                        .fileName(fileName)
                        .minioPath(minioPath)
                        .bucket(minioStorageService.getRawBucket())
                        .fileSize(fileSize)
                        .contentType("application/octet-stream")
                        .createdAt(Instant.now())
                        .build();

                kafkaProducerService.publishFileReady(event);
                log.info("Downloaded and published: jobId={}, file={}", message.getJobId(), fileName);
            }
        } catch (Exception e) {
            log.error("Error downloading file: {}, error={}", remotePath, e.getMessage(), e);
        }
    }

    private String extractFileName(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }
}
