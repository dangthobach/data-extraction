package com.extraction.iam.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ValidateResponse {
    private boolean valid;
    private String clientId;
    private String clientName;
    private String scopes;
    private Integer dailyLimit;
    private String message;
}
