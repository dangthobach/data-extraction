package com.extraction.iam.repository;

import com.extraction.iam.entity.SystemClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SystemClientRepository extends JpaRepository<SystemClient, UUID> {
    Optional<SystemClient> findByClientId(String clientId);
}
