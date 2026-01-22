package com.extraction.executor.client;

import com.extraction.executor.config.FeignClientConfig;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * OpenFeign client for ETL Engine
 *
 * - Tích hợp Resilience4j (CircuitBreaker + Retry)
 * - Sử dụng cấu hình chung FeignClientConfig (logging, timeout, encoder/decoder...)
 * - Có fallback rõ ràng cho từng API để monitor lỗi dễ dàng
 */
@FeignClient(
        name = "etl-engine-client",
        url = "${etl-engine.url:http://localhost:8089}",
        configuration = FeignClientConfig.class
)
public interface EtlClient {

    /**
     * Stage 1: Split & Rename
     */
    @PostMapping("/api/v1/documents/split-rename")
    @CircuitBreaker(name = "documentProcessing", fallbackMethod = "splitRenameFallback")
    @Retry(name = "documentProcessing")
    Map<String, Object> splitRename(@RequestBody Map<String, Object> request);

    /**
     * Stage 2: Check Completeness
     */
    @PostMapping("/api/v1/documents/check-completeness")
    @CircuitBreaker(name = "documentProcessing", fallbackMethod = "checkCompletenessFallback")
    @Retry(name = "documentProcessing")
    Map<String, Object> checkCompleteness(@RequestBody Map<String, Object> request);

    /**
     * Stage 3: Extract Data
     */
    @PostMapping("/api/v1/documents/extract-data")
    @CircuitBreaker(name = "documentProcessing", fallbackMethod = "extractDataFallback")
    @Retry(name = "documentProcessing")
    Map<String, Object> extractData(@RequestBody Map<String, Object> request);

    /**
     * Stage 4: Cross Check
     */
    @PostMapping("/api/v1/documents/cross-check")
    @CircuitBreaker(name = "documentProcessing", fallbackMethod = "crossCheckFallback")
    @Retry(name = "documentProcessing")
    Map<String, Object> crossCheck(@RequestBody Map<String, Object> request);

    // ===== Fallback methods (CircuitBreaker/Retry) =====

    /**
     * Fallback cho splitRename khi ETL Engine timeout / lỗi / circuit open.
     */
    default Map<String, Object> splitRenameFallback(Map<String, Object> request, Exception ex) {
        throw new RuntimeException(
                "ETL Engine hiện không khả dụng cho thao tác split-rename. "
                        + "Vui lòng thử lại sau. Error: " + ex.getMessage(), ex);
    }

    /**
     * Fallback cho checkCompleteness khi ETL Engine timeout / lỗi / circuit open.
     */
    default Map<String, Object> checkCompletenessFallback(Map<String, Object> request, Exception ex) {
        throw new RuntimeException(
                "ETL Engine hiện không khả dụng cho thao tác check-completeness. "
                        + "Vui lòng thử lại sau. Error: " + ex.getMessage(), ex);
    }

    /**
     * Fallback cho extractData khi ETL Engine timeout / lỗi / circuit open.
     */
    default Map<String, Object> extractDataFallback(Map<String, Object> request, Exception ex) {
        throw new RuntimeException(
                "ETL Engine hiện không khả dụng cho thao tác extract-data. "
                        + "Vui lòng thử lại sau. Error: " + ex.getMessage(), ex);
    }

    /**
     * Fallback cho crossCheck khi ETL Engine timeout / lỗi / circuit open.
     */
    default Map<String, Object> crossCheckFallback(Map<String, Object> request, Exception ex) {
        throw new RuntimeException(
                "ETL Engine hiện không khả dụng cho thao tác cross-check. "
                        + "Vui lòng thử lại sau. Error: " + ex.getMessage(), ex);
    }
}
