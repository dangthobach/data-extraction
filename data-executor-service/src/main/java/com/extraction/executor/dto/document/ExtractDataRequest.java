package com.extraction.executor.dto.document;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for Extract Data API
 * Endpoint: POST /api/v1/documents/extract-data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractDataRequest {

    /**
     * Transaction ID from the split-rename operation
     * Example: "TRX_20260116_123410_b27ec6"
     */
    @JsonProperty("transaction_id")
    private String transactionId;
}
