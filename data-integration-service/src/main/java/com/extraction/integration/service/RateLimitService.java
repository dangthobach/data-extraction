package com.extraction.integration.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${rate-limit.daily-limit:100}")
    private int dailyLimit;

    @Value("${rate-limit.key-prefix:rate_limit:}")
    private String keyPrefix;

    // Lua script for atomic increment and check
    private static final String RATE_LIMIT_SCRIPT = """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('EXPIRE', KEYS[1], 86400)
            end
            if current > tonumber(ARGV[1]) then
                return 0
            else
                return 1
            end
            """;

    private final DefaultRedisScript<Long> rateLimitScript = new DefaultRedisScript<>(RATE_LIMIT_SCRIPT, Long.class);

    /**
     * Check and increment rate limit for a system
     * 
     * @param systemId The system identifier
     * @return true if request is allowed, false if rate limited
     */
    public boolean checkAndIncrementRateLimit(String systemId) {
        String key = buildKey(systemId);

        try {
            Long result = redisTemplate.execute(
                    rateLimitScript,
                    Collections.singletonList(key),
                    dailyLimit);

            boolean allowed = result != null && result == 1L;

            if (!allowed) {
                log.warn("Rate limit exceeded for system: {}", systemId);
            }

            return allowed;
        } catch (Exception e) {
            log.error("Error checking rate limit for system: {}", systemId, e);
            // Fail open - allow request if Redis is unavailable
            return true;
        }
    }

    /**
     * Get current usage count for a system
     */
    public int getCurrentUsage(String systemId) {
        String key = buildKey(systemId);
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? Integer.parseInt(value.toString()) : 0;
    }

    /**
     * Get remaining quota for a system
     */
    public int getRemainingQuota(String systemId) {
        int used = getCurrentUsage(systemId);
        return Math.max(0, dailyLimit - used);
    }

    private String buildKey(String systemId) {
        String date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        return keyPrefix + systemId + ":" + date;
    }
}
