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
public class IngestRequestMessage {

    private String jobId;
    private String requestId;
    private String systemId;
    private RequestType type;
    private String sourcePath;
    private String sourceConfig;
    private Instant createdAt;

    public enum RequestType {
        UPLOAD,
        SYNC
    }
}
