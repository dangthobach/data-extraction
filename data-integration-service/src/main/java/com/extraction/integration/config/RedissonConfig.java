package com.extraction.integration.config;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.redisson.Bucket4jRedisson;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Redisson and Bucket4j Configuration
 * 
 * Configures:
 * - RedissonClient for distributed operations
 * - ProxyManager for Bucket4j distributed rate limiting
 * 
 * Compatible with JDK 17+ (including JDK 21)
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Bean(destroyMethod = "shutdown")
    @Primary
    public RedissonClient redissonClient() {
        Config config = new Config();

        String address = "redis://" + redisHost + ":" + redisPort;

        config.useSingleServer()
                .setAddress(address)
                .setPassword(redisPassword.isEmpty() ? null : redisPassword)
                .setConnectionPoolSize(64)
                .setConnectionMinimumIdleSize(10)
                .setConnectTimeout(3000)
                .setTimeout(3000)
                .setRetryAttempts(2)
                .setRetryInterval(1000);

        return Redisson.create(config);
    }

    @Bean
    public ProxyManager<String> rateLimitProxyManager(RedissonClient redissonClient) {
        // Cast to Redisson implementation to access getCommandExecutor()
        return Bucket4jRedisson.casBasedBuilder(((Redisson) redissonClient).getCommandExecutor())
                .build();
    }
}
