package com.extraction.executor.service;

import io.minio.*;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
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
     * Move file from temp bucket to raw bucket
     */
    public String moveToRawBucket(String sourcePath) throws Exception {
        String[] parts = sourcePath.split("/", 2);
        String sourceBucket = parts[0];
        String sourceObject = parts[1];

        String destObject = "raw/" + sourceObject;

        // Copy to raw bucket
        minioClient.copyObject(CopyObjectArgs.builder()
                .bucket(rawBucket)
                .object(destObject)
                .source(CopySource.builder()
                        .bucket(sourceBucket)
                        .object(sourceObject)
                        .build())
                .build());

        // Delete from temp
        minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(sourceBucket)
                .object(sourceObject)
                .build());

        log.info("Moved file to raw bucket: {}/{}", rawBucket, destObject);
        return rawBucket + "/" + destObject;
    }

    /**
     * Upload file to raw bucket
     */
    public String uploadToRaw(InputStream inputStream, String fileName, String systemId, long size, String contentType)
            throws Exception {
        String objectName = buildObjectPath(systemId, fileName);

        minioClient.putObject(PutObjectArgs.builder()
                .bucket(rawBucket)
                .object(objectName)
                .stream(inputStream, size, -1)
                .contentType(contentType)
                .build());

        log.info("Uploaded file to raw bucket: {}/{}", rawBucket, objectName);
        return rawBucket + "/" + objectName;
    }

    /**
     * Get object info
     */
    public StatObjectResponse getObjectInfo(String path) throws Exception {
        String[] parts = path.split("/", 2);
        return minioClient.statObject(StatObjectArgs.builder()
                .bucket(parts[0])
                .object(parts[1])
                .build());
    }

    /**
     * Get object stream
     */
    public InputStream getObject(String path) throws Exception {
        String[] parts = path.split("/", 2);
        return minioClient.getObject(GetObjectArgs.builder()
                .bucket(parts[0])
                .object(parts[1])
                .build());
    }

    /**
     * List objects in a path
     */
    public List<String> listObjects(String bucket, String prefix) {
        List<String> objects = new ArrayList<>();
        Iterable<Result<Item>> results = minioClient.listObjects(ListObjectsArgs.builder()
                .bucket(bucket)
                .prefix(prefix)
                .recursive(true)
                .build());

        try {
            for (Result<Item> result : results) {
                objects.add(result.get().objectName());
            }
        } catch (Exception e) {
            log.error("Error listing objects: {}", e.getMessage(), e);
        }

        return objects;
    }

    private String buildObjectPath(String systemId, String fileName) {
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return String.format("%s/%s/%s_%s",
                systemId,
                java.time.LocalDate.now().toString(),
                uuid,
                fileName);
    }

    public String getRawBucket() {
        return rawBucket;
    }
}
