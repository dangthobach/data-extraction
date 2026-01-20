package com.extraction.executor.dto.document;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for Check Completeness API
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckCompletenessResponse {

    @JsonProperty("check_result")
    private CheckResult checkResult;

    /**
     * Nested DTO for check result details
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckResult {

        @JsonProperty("status")
        private String status; // "completed" or "incompleted"

        @JsonProperty("missing_documents")
        private List<String> missingDocuments;

        @JsonProperty("available_documents")
        private List<AvailableDocument> availableDocuments;
    }

    /**
     * Nested DTO for available document information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AvailableDocument {

        @JsonProperty("document_name")
        private String documentName;

        @JsonProperty("file_name")
        private String fileName;
    }
}
