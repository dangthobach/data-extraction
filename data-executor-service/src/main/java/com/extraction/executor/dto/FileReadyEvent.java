package com.extraction.executor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileReadyEvent {

    private String jobId;
    private String fileId;
    private String systemId;
    private String fileName;
    private String minioPath;
    private String bucket;
    private long fileSize;
    private String contentType;
    private Instant createdAt;
}
