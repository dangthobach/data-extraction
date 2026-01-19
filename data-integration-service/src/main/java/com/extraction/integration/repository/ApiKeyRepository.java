package com.extraction.integration.repository;

import com.extraction.integration.entity.ApiKey;
import com.extraction.integration.entity.ApiKeyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for API Key management.
 */
@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    /**
     * Find API Key by its hash
     */
    Optional<ApiKey> findByApiKeyHash(String apiKeyHash);

    /**
     * Find all keys for a system
     */
    List<ApiKey> findBySystemId(String systemId);

    /**
     * Find active keys for a system
     */
    List<ApiKey> findBySystemIdAndStatus(String systemId, ApiKeyStatus status);

    /**
     * Check if a key hash exists
     */
    boolean existsByApiKeyHash(String apiKeyHash);

    /**
     * Update last used timestamp
     */
    @Modifying
    @Query("UPDATE ApiKey a SET a.lastUsedAt = :timestamp WHERE a.apiKeyHash = :hash")
    void updateLastUsedAt(@Param("hash") String apiKeyHash, @Param("timestamp") Instant timestamp);
}
