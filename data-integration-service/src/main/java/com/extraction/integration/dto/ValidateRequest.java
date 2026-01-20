package com.extraction.integration.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ValidateRequest {
    private String clientId;
    private String clientSecret;
}
