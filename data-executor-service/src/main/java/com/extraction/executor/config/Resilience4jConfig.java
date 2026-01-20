package com.extraction.executor.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Resilience4j configuration for Circuit Breaker and Retry patterns
 */
@Configuration
public class Resilience4jConfig {

    /**
     * Circuit Breaker configuration for Document Processing API
     * 
     * Circuit breaker pattern prevents cascading failures by opening
     * the circuit when failure threshold is reached
     */
    @Bean
    public CircuitBreakerConfig documentProcessingCircuitBreakerConfig() {
        return CircuitBreakerConfig.custom()
                // Open circuit if 50% of requests fail
                .failureRateThreshold(50)
                // Minimum number of calls before calculating failure rate
                .minimumNumberOfCalls(5)
                // Wait 30 seconds before transitioning to half-open state
                .waitDurationInOpenState(Duration.ofSeconds(30))
                // Allow 3 test calls in half-open state
                .permittedNumberOfCallsInHalfOpenState(3)
                // Use a sliding window of 10 calls to calculate failure rate
                .slidingWindowSize(10)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .build();
    }

    /**
     * Retry configuration for Document Processing API
     * 
     * Retry pattern handles transient failures by automatically
     * retrying failed requests with exponential backoff
     */
    @Bean
    public RetryConfig documentProcessingRetryConfig() {
        return RetryConfig.custom()
                // Maximum 3 retry attempts
                .maxAttempts(3)
                // Initial wait duration: 500ms
                .waitDuration(Duration.ofMillis(500))
                // Exponential backoff with multiplier 2
                // Retry intervals: 500ms, 1000ms, 2000ms
                .intervalFunction(io.github.resilience4j.core.IntervalFunction
                        .ofExponentialBackoff(500, 2))
                // Retry on specific exceptions
                .retryExceptions(
                        feign.RetryableException.class,
                        java.net.SocketTimeoutException.class,
                        java.io.IOException.class)
                .build();
    }
}
