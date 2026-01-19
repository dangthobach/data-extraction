package com.extraction.integration.repository;

import com.extraction.integration.entity.Job;
import com.extraction.integration.entity.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
 * Repository for Job management.
 */
@Repository
public interface JobRepository extends JpaRepository<Job, UUID> {

    /**
     * Find jobs by system ID with pagination
     */
    Page<Job> findBySystemIdOrderByCreatedAtDesc(String systemId, Pageable pageable);

    /**
     * Find jobs by system and status
     */
    List<Job> findBySystemIdAndStatusOrderByCreatedAtDesc(String systemId, JobStatus status);

    /**
     * Find job by request ID (for correlation)
     */
    Optional<Job> findByRequestId(String requestId);

    /**
     * Count pending jobs for a system
     */
    long countBySystemIdAndStatus(String systemId, JobStatus status);

    /**
     * Update job status
     */
    @Modifying
    @Query("UPDATE Job j SET j.status = :status, j.updatedAt = :now WHERE j.id = :id")
    int updateStatus(@Param("id") UUID id, @Param("status") JobStatus status, @Param("now") Instant now);

    /**
     * Mark job as completed
     */
    @Modifying
    @Query("UPDATE Job j SET j.status = 'COMPLETED', j.progress = 100, j.completedAt = :now, j.updatedAt = :now WHERE j.id = :id")
    int markCompleted(@Param("id") UUID id, @Param("now") Instant now);

    /**
     * Mark job as failed
     */
    @Modifying
    @Query("UPDATE Job j SET j.status = 'FAILED', j.errorMessage = :error, j.completedAt = :now, j.updatedAt = :now WHERE j.id = :id")
    int markFailed(@Param("id") UUID id, @Param("error") String error, @Param("now") Instant now);
}
