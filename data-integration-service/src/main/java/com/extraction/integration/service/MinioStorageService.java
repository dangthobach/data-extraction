package com.extraction.integration.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.minio.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket.temp}")
    private String tempBucket;

    @Value("${minio.bucket.raw}")
    private String rawBucket;

    @PostConstruct
    public void init() {
        createBucketIfNotExists(tempBucket);
        createBucketIfNotExists(rawBucket);
    }

    private void createBucketIfNotExists(String bucketName) {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("Created bucket: {}", bucketName);
            }
        } catch (Exception e) {
            log.error("Failed to create bucket: {}", bucketName, e);
        }
    }

    /**
     * Upload a file to the temp bucket with Circuit Breaker protection
     * 
     * @return The object path in MinIO
     */
    @CircuitBreaker(name = "minioService", fallbackMethod = "uploadFallback")
    public String uploadToTemp(MultipartFile file, String systemId) throws Exception {
        String objectName = buildObjectPath(systemId, file.getOriginalFilename());

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(tempBucket)
                    .object(objectName)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        }

        log.info("Uploaded file to MinIO: bucket={}, path={}, size={}",
                tempBucket, objectName, file.getSize());
        return tempBucket + "/" + objectName;
    }

    /**
     * Upload from InputStream to temp bucket with Circuit Breaker
     */
    @CircuitBreaker(name = "minioService", fallbackMethod = "uploadStreamFallback")
    public String uploadToTemp(InputStream inputStream, String fileName, String systemId, long size, String contentType)
            throws Exception {
        String objectName = buildObjectPath(systemId, fileName);

        minioClient.putObject(PutObjectArgs.builder()
                .bucket(tempBucket)
                .object(objectName)
                .stream(inputStream, size, -1)
                .contentType(contentType)
                .build());

        log.info("Uploaded file to MinIO: bucket={}, path={}", tempBucket, objectName);
        return tempBucket + "/" + objectName;
    }

    /**
     * Fallback when MinIO circuit is open
     */
    public String uploadFallback(MultipartFile file, String systemId, Throwable throwable) {
        log.error("MinIO circuit breaker open, upload failed: file={}, error={}",
                file.getOriginalFilename(), throwable.getMessage());
        throw new RuntimeException("Storage service temporarily unavailable. Please retry later.");
    }

    public String uploadStreamFallback(InputStream inputStream, String fileName, String systemId, long size,
            String contentType, Throwable throwable) {
        log.error("MinIO circuit breaker open, stream upload failed: file={}, error={}",
                fileName, throwable.getMessage());
        throw new RuntimeException("Storage service temporarily unavailable. Please retry later.");
    }

    private String buildObjectPath(String systemId, String fileName) {
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return String.format("%s/%s/%s_%s",
                systemId,
                java.time.LocalDate.now().toString(),
                uuid,
                fileName);
    }
}
