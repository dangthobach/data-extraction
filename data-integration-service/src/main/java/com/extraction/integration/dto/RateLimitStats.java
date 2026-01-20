package com.extraction.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for rate limit statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitStats {
    private String systemId;
    private long limit;
    private long used;
    private long remaining;
    private double percentUsed;
    private String error;
}
