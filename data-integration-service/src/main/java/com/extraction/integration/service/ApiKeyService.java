package com.extraction.integration.service;

import com.extraction.integration.dto.SystemInfo;
import com.extraction.integration.entity.ApiKey;
import com.extraction.integration.exception.UnauthorizedException;
import com.extraction.integration.repository.ApiKeyRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Service for API Key validation with multi-level caching.
 * 
 * Cache Strategy:
 * - L1 (Caffeine): In-memory, 10 minutes TTL, zero-latency
 * - L2 (Redis): Distributed, 1 hour TTL, cross-instance consistency
 * - Database: Source of truth
 * 
 * Flow: Check L1 -> Check L2 -> Query DB -> Populate caches
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String REDIS_KEY_PREFIX = "api_key:";
    private static final Duration REDIS_TTL = Duration.ofHours(1);
    private static final Duration CAFFEINE_TTL = Duration.ofMinutes(10);

    // L1 Cache - Caffeine (in-memory)
    private Cache<String, SystemInfo> l1Cache;

    @PostConstruct
    void init() {
        l1Cache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(CAFFEINE_TTL)
                .recordStats()
                .build();
        log.info("API Key L1 cache initialized with TTL: {}", CAFFEINE_TTL);
    }

    /**
     * Validate API Key and return system information.
     * Uses multi-level caching for performance.
     *
     * @param apiKey The raw API key from request header
     * @return SystemInfo containing validated system details
     * @throws UnauthorizedException if key is invalid, expired, or revoked
     */
    public SystemInfo validateApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new UnauthorizedException("API key is required");
        }

        String keyHash = hashApiKey(apiKey);

        // Level 1: Check Caffeine cache
        SystemInfo cached = l1Cache.getIfPresent(keyHash);
        if (cached != null) {
            log.debug("API Key found in L1 cache for system: {}", cached.getSystemId());
            return SystemInfo.builder()
                    .systemId(cached.getSystemId())
                    .systemName(cached.getSystemName())
                    .dailyLimit(cached.getDailyLimit())
                    .cachedL1(true)
                    .cachedL2(false)
                    .build();
        }

        // Level 2: Check Redis cache
        SystemInfo fromRedis = getFromRedis(keyHash);
        if (fromRedis != null) {
            log.debug("API Key found in L2 cache for system: {}", fromRedis.getSystemId());
            // Populate L1 cache
            l1Cache.put(keyHash, fromRedis);
            return SystemInfo.builder()
                    .systemId(fromRedis.getSystemId())
                    .systemName(fromRedis.getSystemName())
                    .dailyLimit(fromRedis.getDailyLimit())
                    .cachedL1(false)
                    .cachedL2(true)
                    .build();
        }

        // Level 3: Query database
        SystemInfo fromDb = getFromDatabase(keyHash);

        // Populate both caches
        l1Cache.put(keyHash, fromDb);
        saveToRedis(keyHash, fromDb);

        // Update last used timestamp asynchronously
        updateLastUsedAsync(keyHash);

        return SystemInfo.builder()
                .systemId(fromDb.getSystemId())
                .systemName(fromDb.getSystemName())
                .dailyLimit(fromDb.getDailyLimit())
                .cachedL1(false)
                .cachedL2(false)
                .build();
    }

    /**
     * Invalidate cached API Key (call when key is revoked/updated)
     */
    public void invalidateApiKey(String apiKey) {
        String keyHash = hashApiKey(apiKey);
        l1Cache.invalidate(keyHash);
        redisTemplate.delete(REDIS_KEY_PREFIX + keyHash);
        log.info("API Key cache invalidated for hash: {}...", keyHash.substring(0, 8));
    }

    /**
     * Get cache statistics for monitoring
     */
    public String getCacheStats() {
        var stats = l1Cache.stats();
        return String.format("L1 Cache - Hits: %d, Misses: %d, HitRate: %.2f%%",
                stats.hitCount(), stats.missCount(), stats.hitRate() * 100);
    }

    // ==================== Private Methods ====================

    private SystemInfo getFromRedis(String keyHash) {
        try {
            Object value = redisTemplate.opsForValue().get(REDIS_KEY_PREFIX + keyHash);
            if (value instanceof SystemInfo) {
                return (SystemInfo) value;
            }
        } catch (Exception e) {
            log.warn("Error reading from Redis cache: {}", e.getMessage());
        }
        return null;
    }

    private void saveToRedis(String keyHash, SystemInfo info) {
        try {
            redisTemplate.opsForValue().set(REDIS_KEY_PREFIX + keyHash, info, REDIS_TTL);
        } catch (Exception e) {
            log.warn("Error writing to Redis cache: {}", e.getMessage());
        }
    }

    private SystemInfo getFromDatabase(String keyHash) {
        Optional<ApiKey> apiKeyOpt = apiKeyRepository.findByApiKeyHash(keyHash);

        if (apiKeyOpt.isEmpty()) {
            log.warn("API Key not found in database");
            throw new UnauthorizedException("Invalid API key");
        }

        ApiKey apiKey = apiKeyOpt.get();

        if (!apiKey.isValid()) {
            log.warn("API Key is not valid: status={}, expired={}",
                    apiKey.getStatus(),
                    apiKey.getExpiresAt() != null && Instant.now().isAfter(apiKey.getExpiresAt()));
            throw new UnauthorizedException("API key is expired or revoked");
        }

        return SystemInfo.builder()
                .systemId(apiKey.getSystemId())
                .systemName(apiKey.getSystemName())
                .dailyLimit(apiKey.getDailyLimit())
                .build();
    }

    @Transactional
    protected void updateLastUsedAsync(String keyHash) {
        try {
            apiKeyRepository.updateLastUsedAt(keyHash, Instant.now());
        } catch (Exception e) {
            log.debug("Failed to update lastUsedAt: {}", e.getMessage());
        }
    }

    /**
     * Hash API key using SHA-256
     */
    private String hashApiKey(String apiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(apiKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
