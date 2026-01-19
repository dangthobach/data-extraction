package com.extraction.integration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriggerJobRequest {

    @NotNull(message = "Source type is required")
    private SourceType sourceType;

    @NotBlank(message = "Source identifier is required")
    private String sourceId;

    private String remotePath; // Optional: specific path to sync
    private String filePattern; // Optional: glob pattern to filter files

    public enum SourceType {
        SFTP,
        S3,
        MINIO
    }
}
