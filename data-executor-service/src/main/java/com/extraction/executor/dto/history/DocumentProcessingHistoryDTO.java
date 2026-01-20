package com.extraction.executor.dto.history;

import com.extraction.executor.entity.ProcessingStage;
import com.extraction.executor.entity.ProcessingStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for Document Processing History API responses
 * (Excludes sensitive data like stack traces)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentProcessingHistoryDTO {

    private Long id;

    @JsonProperty("transaction_id")
    private String transactionId;

    private ProcessingStage stage;

    private ProcessingStatus status;

    @JsonProperty("s3_uri")
    private String s3Uri;

    @JsonProperty("request_payload")
    private String requestPayload;

    @JsonProperty("response_payload")
    private String responsePayload;

    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("processing_time_ms")
    private Long processingTimeMs;

    @JsonProperty("processing_time_human")
    private String processingTimeHuman;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    /**
     * Convert duration in milliseconds to human-readable format
     */
    public static String formatDuration(Long ms) {
        if (ms == null) {
            return null;
        }

        if (ms < 1000) {
            return ms + "ms";
        } else if (ms < 60000) {
            return String.format("%.2fs", ms / 1000.0);
        } else {
            long minutes = ms / 60000;
            long seconds = (ms % 60000) / 1000;
            return String.format("%dm %ds", minutes, seconds);
        }
    }
}
