package com.extraction.integration.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidateResponse {
    private boolean valid;
    private String clientId;
    private String clientName;
    private String scopes;
    private String message;
    private Integer dailyLimit; // Custom daily rate limit for this client
}
