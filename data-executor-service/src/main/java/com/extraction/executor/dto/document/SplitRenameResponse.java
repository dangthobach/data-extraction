package com.extraction.executor.dto.document;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for Split & Rename API
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SplitRenameResponse {

    @JsonProperty("transaction_id")
    private String transactionId;

    @JsonProperty("sub_documents_processed")
    private Integer subDocumentsProcessed;

    @JsonProperty("results")
    private List<SubDocumentResult> results;

    /**
     * Nested DTO for individual document processing result
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubDocumentResult {

        @JsonProperty("category")
        private String category;

        @JsonProperty("name")
        private String name;

        @JsonProperty("final_filename")
        private String finalFilename;

        @JsonProperty("saved_path")
        private String savedPath;
    }
}
