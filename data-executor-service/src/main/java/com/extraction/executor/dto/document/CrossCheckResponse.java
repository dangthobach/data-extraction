package com.extraction.executor.dto.document;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for Cross Check API
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrossCheckResponse {

    @JsonProperty("status")
    private String status; // "success" or "failed"

    @JsonProperty("message")
    private String message;

    @JsonProperty("transaction_id")
    private String transactionId;

    @JsonProperty("cross_check_result")
    private CrossCheckResult crossCheckResult;

    /**
     * Nested DTO for cross check result details
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CrossCheckResult {

        @JsonProperty("is_consistent")
        private Boolean isConsistent;

        @JsonProperty("inconsistencies")
        private java.util.List<String> inconsistencies;

        @JsonProperty("matched_fields")
        private java.util.List<String> matchedFields;
    }
}
