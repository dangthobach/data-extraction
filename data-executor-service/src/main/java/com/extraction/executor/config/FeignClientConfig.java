package com.extraction.executor.config;

import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import feign.micrometer.MicrometerCapability;
import feign.okhttp.OkHttpClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.okhttp3.OkHttpMetricsEventListener;
import okhttp3.ConnectionPool;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Enhanced OpenFeign Configuration
 * 
 * Includes production-grade features:
 * - Request interceptor for correlation ID (distributed tracing)
 * - Micrometer metrics for Prometheus monitoring
 * - OkHttp connection pooling for performance
 * - Environment-aware logging
 */
@Configuration
public class FeignClientConfig {

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    /**
     * Environment-aware Feign logging level
     * - Production: BASIC (minimal overhead)
     * - Development: FULL (detailed debugging)
     */
    @Bean
    public Logger.Level feignLoggerLevel() {
        return "prod".equals(activeProfile) || "production".equals(activeProfile)
                ? Logger.Level.BASIC
                : Logger.Level.FULL;
    }

    /**
     * Connection timeout and read timeout configuration
     */
    @Bean
    public Request.Options requestOptions() {
        // Connect timeout: 5 seconds
        // Read timeout: 30 seconds
        return new Request.Options(
                5000L,
                TimeUnit.MILLISECONDS,
                30000L,
                TimeUnit.MILLISECONDS,
                true // followRedirects
        );
    }

    /**
     * Custom error decoder for handling API errors
     */
    @Bean
    public ErrorDecoder errorDecoder() {
        return new DocumentProcessingErrorDecoder();
    }

    /**
     * Request interceptor for adding correlation ID and common headers
     * 
     * Enables distributed tracing by propagating correlation ID through
     * all HTTP requests, making it easy to trace requests across services.
     */
    @Bean
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            // Try to get correlation ID from MDC (if set by previous request)
            String correlationId = MDC.get("correlationId");
            if (correlationId == null || correlationId.isEmpty()) {
                correlationId = UUID.randomUUID().toString();
            }

            // Add correlation ID header
            requestTemplate.header("X-Correlation-ID", correlationId);

            // Add service identification headers
            requestTemplate.header("X-Service-Name", "executor-service");
            requestTemplate.header("X-Request-Timestamp", Instant.now().toString());
        };
    }

    /**
     * Micrometer capability for metrics collection
     * 
     * Automatically exposes Feign client metrics to Prometheus:
     * - feign_Client_seconds (request duration histogram)
     * - feign_Client_seconds_count (total request count)
     * - feign_Client_seconds_sum (total time spent)
     * 
     * Tagged with: client, method, uri, status
     */
    @Bean
    public MicrometerCapability micrometerCapability(MeterRegistry registry) {
        return new MicrometerCapability(registry);
    }

    /**
     * OkHttp client with connection pool for better performance
     * 
     * Benefits:
     * - Connection reuse reduces latency
     * - Pool of 50 connections with 5-minute keep-alive
     * - Retry on connection failures
     * - Metrics via OkHttp instrumentation
     */
    @Bean
    public feign.Client feignClient(MeterRegistry registry) {
        okhttp3.OkHttpClient okHttpClient = new okhttp3.OkHttpClient.Builder()
                // Connection pool: max 50 connections, keep alive for 5 minutes
                .connectionPool(new ConnectionPool(50, 5, TimeUnit.MINUTES))
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                // Add metrics event listener for OkHttp-level metrics
                .eventListener(OkHttpMetricsEventListener.builder(registry, "okhttp.requests")
                        .build())
                .build();

        return new OkHttpClient(okHttpClient);
    }
}
