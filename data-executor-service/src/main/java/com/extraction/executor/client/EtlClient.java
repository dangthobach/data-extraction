package com.extraction.executor.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.Map;

@FeignClient(name = "etl-engine", url = "${etl-engine.url:http://localhost:8089}")
public interface EtlClient {

    @PostMapping("/api/v1/documents/split-rename")
    Map<String, Object> splitRename(@RequestBody Map<String, Object> request);

    @PostMapping("/api/v1/documents/check-completeness")
    Map<String, Object> checkCompleteness(@RequestBody Map<String, Object> request);

    @PostMapping("/api/v1/documents/extract-data")
    Map<String, Object> extractData(@RequestBody Map<String, Object> request);

    @PostMapping("/api/v1/documents/cross-check")
    Map<String, Object> crossCheck(@RequestBody Map<String, Object> request);
}
