package com.extraction.integration.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing an API Key for third-party system authentication.
 * Stored in database with hash for security.
 */
@Entity
@Table(name = "api_keys", indexes = {
        @Index(name = "idx_api_keys_hash", columnList = "apiKeyHash"),
        @Index(name = "idx_api_keys_system", columnList = "systemId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * SHA-256 hash of the API key (never store plain text)
     */
    @Column(nullable = false, unique = true, length = 128)
    private String apiKeyHash;

    /**
     * System identifier for rate limiting and tracking
     */
    @Column(nullable = false, length = 50)
    private String systemId;

    /**
     * Human-readable system name
     */
    @Column(length = 100)
    private String systemName;

    /**
     * Key status
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ApiKeyStatus status = ApiKeyStatus.ACTIVE;

    /**
     * Daily request limit (overrides global default)
     */
    @Builder.Default
    private Integer dailyLimit = 100;

    @Column(updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    /**
     * Optional expiration date
     */
    private Instant expiresAt;

    /**
     * Track last usage for monitoring
     */
    private Instant lastUsedAt;

    /**
     * Check if this API key is valid (active and not expired)
     */
    public boolean isValid() {
        if (status != ApiKeyStatus.ACTIVE) {
            return false;
        }
        if (expiresAt != null && Instant.now().isAfter(expiresAt)) {
            return false;
        }
        return true;
    }
}
