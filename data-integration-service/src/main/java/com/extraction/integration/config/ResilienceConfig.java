package com.extraction.integration.config;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class ResilienceConfig {

    /**
     * Configure event listeners for Circuit Breaker state changes
     */
    /**
     * Configure event listeners for Circuit Breaker using RegistryEventConsumer
     * This avoids circular dependencies caused by injecting the Registry into a
     * Bean that returns the Registry.
     */
    @Bean
    public RegistryEventConsumer<io.github.resilience4j.circuitbreaker.CircuitBreaker> circuitBreakerEventConsumer() {
        return new RegistryEventConsumer<io.github.resilience4j.circuitbreaker.CircuitBreaker>() {
            @Override
            public void onEntryAddedEvent(
                    io.github.resilience4j.core.registry.EntryAddedEvent<io.github.resilience4j.circuitbreaker.CircuitBreaker> entryAddedEvent) {
                var cb = entryAddedEvent.getAddedEntry();
                cb.getEventPublisher()
                        .onStateTransition(event -> log.warn("CircuitBreaker {} state changed: {} -> {}",
                                cb.getName(),
                                event.getStateTransition().getFromState(),
                                event.getStateTransition().getToState()))
                        .onError(event -> log.error("CircuitBreaker {} error: {}",
                                cb.getName(), event.getThrowable().getMessage()))
                        .onSuccess(event -> log.debug("CircuitBreaker {} success, duration: {}ms",
                                cb.getName(), event.getElapsedDuration().toMillis()));
            }

            @Override
            public void onEntryRemovedEvent(
                    io.github.resilience4j.core.registry.EntryRemovedEvent<io.github.resilience4j.circuitbreaker.CircuitBreaker> entryRemovedEvent) {
            }

            @Override
            public void onEntryReplacedEvent(
                    io.github.resilience4j.core.registry.EntryReplacedEvent<io.github.resilience4j.circuitbreaker.CircuitBreaker> entryReplacedEvent) {
            }
        };
    }

    /**
     * Configure event listeners for Bulkhead using RegistryEventConsumer
     */
    @Bean
    public RegistryEventConsumer<io.github.resilience4j.bulkhead.Bulkhead> bulkheadEventConsumer() {
        return new RegistryEventConsumer<io.github.resilience4j.bulkhead.Bulkhead>() {
            @Override
            public void onEntryAddedEvent(
                    io.github.resilience4j.core.registry.EntryAddedEvent<io.github.resilience4j.bulkhead.Bulkhead> entryAddedEvent) {
                var bh = entryAddedEvent.getAddedEntry();
                bh.getEventPublisher()
                        .onCallRejected(event -> log.warn("Bulkhead {} rejected call", bh.getName()));
            }

            @Override
            public void onEntryRemovedEvent(
                    io.github.resilience4j.core.registry.EntryRemovedEvent<io.github.resilience4j.bulkhead.Bulkhead> entryRemovedEvent) {
            }

            @Override
            public void onEntryReplacedEvent(
                    io.github.resilience4j.core.registry.EntryReplacedEvent<io.github.resilience4j.bulkhead.Bulkhead> entryReplacedEvent) {
            }
        };
    }
}
