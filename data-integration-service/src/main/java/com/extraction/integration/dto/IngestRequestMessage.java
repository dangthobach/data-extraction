package com.extraction.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestRequestMessage {

    private String jobId;
    private String requestId;
    private String systemId;
    private RequestType type;
    private String sourcePath; // For UPLOAD: MinIO temp path; For SYNC: source identifier
    private String sourceConfig; // JSON with SFTP/S3 connection details (for SYNC)
    private Instant createdAt;

    public enum RequestType {
        UPLOAD, // File uploaded via API
        SYNC // Event trigger to pull from external source
    }
}
