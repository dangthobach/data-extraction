package com.extraction.integration.service;

import com.extraction.integration.client.IamClient;
import com.extraction.integration.dto.SystemInfo;
import com.extraction.integration.dto.ValidateRequest;
import com.extraction.integration.dto.ValidateResponse;
import com.extraction.integration.exception.UnauthorizedException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Service for IAM validation with multi-level caching.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IamAuthService {

    private final IamClient iamClient;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String REDIS_KEY_PREFIX = "iam_auth:";
    private static final Duration REDIS_TTL = Duration.ofHours(1);
    private static final Duration CAFFEINE_TTL = Duration.ofMinutes(10);

    // L1 Cache - Caffeine
    private Cache<String, SystemInfo> l1Cache;

    @PostConstruct
    void init() {
        l1Cache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(CAFFEINE_TTL)
                .recordStats()
                .build();
    }

    public SystemInfo validate(String token) {
        if (token == null || token.isBlank()) {
            throw new UnauthorizedException("Token is required");
        }

        // Hash token to use as cache key (shorter than full token)
        String cacheKey = hashToken(token);

        // L1 Cache
        SystemInfo cached = l1Cache.getIfPresent(cacheKey);
        if (cached != null) {
            log.debug("Auth found in L1 cache for system: {}", cached.getSystemId());
            cached.setCachedL1(true);
            return cached;
        }

        // L2 Cache (Redis)
        SystemInfo fromRedis = getFromRedis(cacheKey);
        if (fromRedis != null) {
            log.debug("Auth found in L2 cache for system: {}", fromRedis.getSystemId());
            l1Cache.put(cacheKey, fromRedis);
            fromRedis.setCachedL2(true);
            return fromRedis;
        }

        // Call IAM Service
        try {
            ValidateResponse response = iamClient.validate(ValidateRequest.builder()
                    .token(token)
                    .build());

            if (!response.isValid()) {
                throw new UnauthorizedException("Invalid token: " + response.getMessage());
            }

            SystemInfo info = SystemInfo.builder()
                    .systemId(response.getClientId())
                    .systemName(response.getClientName())
                    .dailyLimit(response.getDailyLimit())
                    .build();

            // Populate caches
            l1Cache.put(cacheKey, info);
            saveToRedis(cacheKey, info);

            return info;

        } catch (Exception e) {
            log.error("IAM validation failed: {}", e.getMessage());
            throw new UnauthorizedException("Authentication failed");
        }
    }

    public String getCacheStats() {
        var stats = l1Cache.stats();
        return String.format("L1 Cache - Hits: %d, Misses: %d, HitRate: %.2f%%",
                stats.hitCount(), stats.missCount(), stats.hitRate() * 100);
    }

    private SystemInfo getFromRedis(String key) {
        try {
            Object val = redisTemplate.opsForValue().get(REDIS_KEY_PREFIX + key);
            if (val instanceof SystemInfo) {
                return (SystemInfo) val;
            }
        } catch (Exception e) {
            log.warn("Redis read failed: {}", e.getMessage());
        }
        return null;
    }

    private void saveToRedis(String key, SystemInfo info) {
        try {
            redisTemplate.opsForValue().set(REDIS_KEY_PREFIX + key, info, REDIS_TTL);
        } catch (Exception e) {
            log.warn("Redis write failed: {}", e.getMessage());
        }
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
