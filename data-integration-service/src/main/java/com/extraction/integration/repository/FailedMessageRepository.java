package com.extraction.integration.repository;

import com.extraction.integration.entity.FailedMessage;
import com.extraction.integration.entity.FailedMessageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for failed message management
 */
@Repository
public interface FailedMessageRepository extends JpaRepository<FailedMessage, UUID> {

    Page<FailedMessage> findByStatusOrderByCreatedAtDesc(FailedMessageStatus status, Pageable pageable);

    List<FailedMessage> findByStatusAndRetryCountLessThan(FailedMessageStatus status, int maxRetries);

    long countByStatus(FailedMessageStatus status);
}
