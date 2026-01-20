package com.extraction.integration.service;

import com.bucket4j.Bandwidth;
import com.bucket4j.Bucket;
import com.bucket4j.BucketConfiguration;
import com.bucket4j.ConsumptionProbe;
import com.bucket4j.distributed.proxy.ProxyManager;
import com.extraction.integration.dto.RateLimitStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Enhanced Rate Limiting Service using Bucket4j + Redisson
 * 
 * Features:
 * - Custom limits per client
 * - Burst protection (1000 req/min)
 * - Distributed rate limiting via Redis
 * - Real-time statistics
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final ProxyManager<String> rateLimitProxyManager;

    @Value("${rate-limit.daily-limit:100000}")
    private int defaultDailyLimit;

    @Value("${rate-limit.burst-limit:1000}")
    private int burstLimit;

    /**
     * Check and increment rate limit for a system
     * 
     * @param systemId    The system identifier
     * @param customLimit Custom daily limit (null = use default)
     * @return true if allowed, false if rate limited
     */
    public boolean checkAndIncrementRateLimit(String systemId, Integer customLimit) {
        int limit = customLimit != null ? customLimit : defaultDailyLimit;
        String bucketKey = buildBucketKey(systemId);

        try {
            BucketConfiguration config = buildBucketConfig(limit);
            Bucket bucket = rateLimitProxyManager.builder()
                    .build(bucketKey, () -> config);

            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

            if (!probe.isConsumed()) {
                log.warn("Rate limit exceeded: systemId={}, remaining={}",
                        systemId, probe.getRemainingTokens());
                return false;
            }

            // Log when usage is high (>80%)
            if (probe.getRemainingTokens() < limit * 0.2) {
                log.info("High usage: systemId={}, remaining={}/{}",
                        systemId, probe.getRemainingTokens(), limit);
            }

            return true;

        } catch (Exception e) {
            log.error("Error checking rate limit for {}: {}", systemId, e.getMessage());
            // Fail-open: allow if Redis error
            return true;
        }
    }

    /**
     * Backward compatible method (uses default limit)
     */
    public boolean checkAndIncrementRateLimit(String systemId) {
        return checkAndIncrementRateLimit(systemId, null);
    }

    /**
     * Get current usage for a system
     */
    public int getCurrentUsage(String systemId) {
        return getCurrentUsage(systemId, null);
    }

    /**
     * Get current usage with custom limit
     */
    public int getCurrentUsage(String systemId, Integer customLimit) {
        int limit = customLimit != null ? customLimit : defaultDailyLimit;
        String bucketKey = buildBucketKey(systemId);

        try {
            BucketConfiguration config = buildBucketConfig(limit);
            Bucket bucket = rateLimitProxyManager.builder()
                    .build(bucketKey, () -> config);

            long available = bucket.getAvailableTokens();
            return (int) (limit - available);

        } catch (Exception e) {
            log.error("Error getting usage for {}: {}", systemId, e.getMessage());
            return 0;
        }
    }

    /**
     * Get remaining quota for a system
     */
    public int getRemainingQuota(String systemId) {
        return getRemainingQuota(systemId, null);
    }

    /**
     * Get remaining quota with custom limit
     */
    public int getRemainingQuota(String systemId, Integer customLimit) {
        int limit = customLimit != null ? customLimit : defaultDailyLimit;
        String bucketKey = buildBucketKey(systemId);

        try {
            BucketConfiguration config = buildBucketConfig(limit);
            Bucket bucket = rateLimitProxyManager.builder()
                    .build(bucketKey, () -> config);

            return (int) bucket.getAvailableTokens();

        } catch (Exception e) {
            log.error("Error getting remaining quota for {}: {}", systemId, e.getMessage());
            return limit; // Return full quota on error
        }
    }

    /**
     * Get detailed statistics for a system
     */
    public RateLimitStats getStats(String systemId, Integer customLimit) {
        int limit = customLimit != null ? customLimit : defaultDailyLimit;
        String bucketKey = buildBucketKey(systemId);

        try {
            BucketConfiguration config = buildBucketConfig(limit);
            Bucket bucket = rateLimitProxyManager.builder()
                    .build(bucketKey, () -> config);

            long available = bucket.getAvailableTokens();
            long used = limit - available;

            return RateLimitStats.builder()
                    .systemId(systemId)
                    .limit(limit)
                    .used(used)
                    .remaining(available)
                    .percentUsed((double) used / limit * 100)
                    .build();

        } catch (Exception e) {
            log.error("Error getting stats for {}: {}", systemId, e.getMessage());
            return RateLimitStats.builder()
                    .systemId(systemId)
                    .limit(limit)
                    .error(e.getMessage())
                    .build();
        }
    }

    /**
     * Build bucket key with date for daily reset
     */
    private String buildBucketKey(String systemId) {
        String date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        return "ratelimit:" + systemId + ":" + date;
    }

    /**
     * Build bucket configuration with dual-band limiting
     */
    private BucketConfiguration buildBucketConfig(int dailyLimit) {
        return BucketConfiguration.builder()
                // Daily limit: 100K requests/day
                .addLimit(Bandwidth.builder()
                        .capacity(dailyLimit)
                        .refillGreedy(dailyLimit, Duration.ofDays(1))
                        .build())
                // Burst limit: 1000 requests/minute (protect against spike)
                .addLimit(Bandwidth.builder()
                        .capacity(burstLimit)
                        .refillGreedy(burstLimit, Duration.ofMinutes(1))
                        .build())
                .build();
    }
}
