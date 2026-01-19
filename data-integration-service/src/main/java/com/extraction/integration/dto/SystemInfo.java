package com.extraction.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * DTO containing validated system information from API Key.
 * Used after successful authentication.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemInfo implements Serializable {

    private String systemId;
    private String systemName;
    private Integer dailyLimit;

    /**
     * Whether the API key was found in L1 cache (Caffeine)
     */
    private boolean cachedL1;

    /**
     * Whether the API key was found in L2 cache (Redis)
     */
    private boolean cachedL2;
}
