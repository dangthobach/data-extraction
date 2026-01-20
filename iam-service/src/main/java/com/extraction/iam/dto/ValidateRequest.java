package com.extraction.iam.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ValidateRequest {
    @NotBlank
    private String clientId;
    @NotBlank
    private String clientSecret;
}
