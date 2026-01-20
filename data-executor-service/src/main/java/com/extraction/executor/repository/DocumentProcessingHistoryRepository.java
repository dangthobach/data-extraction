package com.extraction.executor.repository;

import com.extraction.executor.entity.DocumentProcessingHistory;
import com.extraction.executor.entity.ProcessingStage;
import com.extraction.executor.entity.ProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for DocumentProcessingHistory entity
 */
@Repository
public interface DocumentProcessingHistoryRepository extends JpaRepository<DocumentProcessingHistory, Long> {

    /**
     * Find all history records for a specific transaction ID
     * Ordered by creation time (oldest first)
     * 
     * @param transactionId Transaction ID to search for
     * @return List of history records
     */
    List<DocumentProcessingHistory> findByTransactionIdOrderByCreatedAtAsc(String transactionId);

    /**
     * Find a specific stage for a transaction
     * 
     * @param transactionId Transaction ID
     * @param stage         Processing stage
     * @return Optional history record
     */
    Optional<DocumentProcessingHistory> findByTransactionIdAndStage(String transactionId, ProcessingStage stage);

    /**
     * Find the latest history record for a transaction
     * 
     * @param transactionId Transaction ID
     * @return Optional latest history record
     */
    @Query("SELECT h FROM DocumentProcessingHistory h WHERE h.transactionId = :transactionId ORDER BY h.createdAt DESC LIMIT 1")
    Optional<DocumentProcessingHistory> findLatestByTransactionId(@Param("transactionId") String transactionId);

    /**
     * Find records by status and created before a certain date (for cleanup)
     * 
     * @param status     Processing status
     * @param cutoffDate Cutoff date
     * @return List of history records
     */
    List<DocumentProcessingHistory> findByStatusAndCreatedAtBefore(ProcessingStatus status, LocalDateTime cutoffDate);

    /**
     * Find all records created between two dates
     * 
     * @param start Start date
     * @param end   End date
     * @return List of history records
     */
    List<DocumentProcessingHistory> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);

    /**
     * Find all records by status
     * 
     * @param status Processing status
     * @return List of history records
     */
    List<DocumentProcessingHistory> findByStatusOrderByCreatedAtDesc(ProcessingStatus status);

    /**
     * Count records by transaction ID
     * 
     * @param transactionId Transaction ID
     * @return Count of records
     */
    long countByTransactionId(String transactionId);

    /**
     * Find records created before a date (for cleanup/archival)
     * 
     * @param cutoffDate Cutoff date
     * @return List of history records
     */
    List<DocumentProcessingHistory> findByCreatedAtBefore(LocalDateTime cutoffDate);
}
