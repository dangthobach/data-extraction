package com.extraction.integration.config;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class ResilienceConfig {

    /**
     * Configure event listeners for Circuit Breaker state changes
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry(CircuitBreakerRegistry registry) {
        registry.getAllCircuitBreakers().forEach(cb -> {
            cb.getEventPublisher()
                    .onStateTransition(event -> log.warn("CircuitBreaker {} state changed: {} -> {}",
                            cb.getName(),
                            event.getStateTransition().getFromState(),
                            event.getStateTransition().getToState()))
                    .onError(event -> log.error("CircuitBreaker {} error: {}",
                            cb.getName(), event.getThrowable().getMessage()))
                    .onSuccess(event -> log.debug("CircuitBreaker {} success, duration: {}ms",
                            cb.getName(), event.getElapsedDuration().toMillis()));
        });
        return registry;
    }

    /**
     * Configure event listeners for Bulkhead
     */
    @Bean
    public BulkheadRegistry bulkheadRegistry(BulkheadRegistry registry) {
        registry.getAllBulkheads().forEach(bh -> {
            bh.getEventPublisher()
                    .onCallRejected(event -> log.warn("Bulkhead {} rejected call", bh.getName()));
        });
        return registry;
    }
}
