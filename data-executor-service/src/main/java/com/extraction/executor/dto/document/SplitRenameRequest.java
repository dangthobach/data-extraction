package com.extraction.executor.dto.document;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for Split & Rename API
 * Endpoint: POST /api/v1/documents/split-rename
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SplitRenameRequest {

    /**
     * S3 URI of the compressed file to be processed
     * Example: "s3://s3-vpbank-model/giai-ngan/2-15-Sao Viet Linh-ELD-33455.zip"
     */
    @JsonProperty("s3_uri")
    private String s3Uri;
}
