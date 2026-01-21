package com.extraction.integration.client;

import com.extraction.integration.dto.ValidateRequest;
import com.extraction.integration.dto.ValidateResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "iam-service", url = "${iam.service.url:http://localhost:8082}", fallback = IamClientFallback.class)
public interface IamClient {

    @PostMapping("/internal/auth/introspect")
    ValidateResponse validate(@RequestBody ValidateRequest request);
}
