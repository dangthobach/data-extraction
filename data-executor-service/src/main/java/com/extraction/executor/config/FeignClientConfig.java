package com.extraction.executor.config;

import feign.Logger;
import feign.Request;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for OpenFeign Clients
 */
@Configuration
public class FeignClientConfig {

    /**
     * Feign logging level
     * NONE: No logging
     * BASIC: Log only request method, URL, response status, and execution time
     * HEADERS: Log basic information along with request and response headers
     * FULL: Log headers, body, and metadata for both request and response
     */
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
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
                java.util.concurrent.TimeUnit.MILLISECONDS,
                30000L,
                java.util.concurrent.TimeUnit.MILLISECONDS,
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
}
