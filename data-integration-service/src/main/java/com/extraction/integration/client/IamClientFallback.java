package com.extraction.integration.client;

import com.extraction.integration.dto.ValidateRequest;
import com.extraction.integration.dto.ValidateResponse;
import org.springframework.stereotype.Component;

@Component
public class IamClientFallback implements IamClient {

    @Override
    public ValidateResponse validate(ValidateRequest request) {
        // Fallback when IAM Service is down
        return ValidateResponse.builder()
                .valid(false)
                .message("IAM Service Unavailable (Fallback)")
                .build();
    }
}
